package me.nallar.javatransformer.api;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import lombok.*;
import me.nallar.javatransformer.internal.ByteCodeInfo;
import me.nallar.javatransformer.internal.SourceInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.*;

@Getter
@Setter
@ToString
public class JavaTransformer {
	private final List<Transformer> transformers = new ArrayList<>();
	private final SimpleMultiMap<String, Transformer> classTransformers = new SimpleMultiMap<>();
	private final Map<String, byte[]> transformedFiles = new HashMap<>();

	private static byte[] readFully(InputStream is) {
		byte[] output = {};
		int position = 0;
		while (true) {
			int bytesToRead;
			if (position >= output.length) {
				bytesToRead = output.length + 4096;
				if (output.length < position + bytesToRead) {
					output = Arrays.copyOf(output, position + bytesToRead);
				}
			} else {
				bytesToRead = output.length - position;
			}
			int bytesRead;
			try {
				bytesRead = is.read(output, position, bytesToRead);
			} catch (IOException e) {
				throw new IOError(e);
			}
			if (bytesRead < 0) {
				if (output.length != position) {
					output = Arrays.copyOf(output, position);
				}
				break;
			}
			position += bytesRead;
		}
		return output;
	}

	public void save(@NonNull Path path) {
		switch (PathType.of(path)) {
			case JAR:
				saveJar(path);
				break;
			case FOLDER:
				saveFolder(path);
				break;
		}
	}

	public void load(@NonNull Path path) {
		load(path, true);
	}

	public void parse(@NonNull Path path) {
		load(path, false);
	}

	private void load(@NonNull Path path, boolean saveTransformedResults) {
		switch (PathType.of(path)) {
			case JAR:
				loadJar(path, saveTransformedResults);
				break;
			case FOLDER:
				loadFolder(path, saveTransformedResults);
				break;
		}
	}

	public void transform(@NonNull Path load, @NonNull Path save) {
		load(load, true);
		save(save);

		clear();
	}

	private void loadFolder(Path input, boolean saveTransformedResults) {
		try {
			Files.walkFileTree(input, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					val relativeName = input.relativize(file).toString();

					val supplier = transformBytes(relativeName, () -> {
						try {
							return Files.readAllBytes(file);
						} catch (IOException e) {
							throw new IOError(e);
						}
					});

					saveTransformedResult(relativeName, supplier, saveTransformedResults);

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new IOError(e);
		}
	}

	private void loadJar(Path p, boolean saveTransformedResults) {
		ZipEntry entry;
		try (ZipInputStream is = new ZipInputStream(new BufferedInputStream(new FileInputStream(p.toFile())))) {
			while ((entry = is.getNextEntry()) != null) {
				saveTransformedResult(entry.getName(), transformBytes(entry.getName(), () -> readFully(is)), saveTransformedResults);
			}
		} catch (IOException e) {
			throw new IOError(e);
		}
	}

	private void saveTransformedResult(String relativeName, Supplier<byte[]> supplier, boolean saveTransformedResults) {
		if (saveTransformedResults)
			transformedFiles.put(relativeName, supplier.get());
	}

	private void saveFolder(Path output) {
		transformedFiles.forEach(((fileName, bytes) -> {
			Path outputFile = output.resolve(fileName);

			try {
				if (Files.exists(outputFile)) {
					throw new IOException("Output file already exists: " + outputFile);
				}
				Files.createDirectories(outputFile.getParent());
				Files.write(outputFile, bytes);
			} catch (IOException e) {
				throw new IOError(e);
			}
		}));
	}

	private void saveJar(Path jar) {
		try (ZipOutputStream os = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(jar.toFile())))) {
			transformedFiles.forEach(((relativeName, bytes) -> {
				try {
					os.putNextEntry(new ZipEntry(relativeName));
					os.write(bytes);
				} catch (IOException e) {
					throw new IOError(e);
				}
			}));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void clear() {
		transformedFiles.clear();
	}

	public void addTransformer(@NonNull Transformer.TargetedTransformer t) {
		if (transformers.contains(t)) {
			throw new IllegalArgumentException("Transformer " + t + " has already been added");
		}

		for (String name : t.getTargetClasses()) {
			classTransformers.put(name, t);
		}
	}

	public void addTransformer(@NonNull String s, @NonNull Transformer t) {
		if (!t.shouldTransform(s)) {
			throw new IllegalArgumentException("Transformer " + t + " must transform class of name " + s);
		}
		if (classTransformers.get(s).contains(t)) {
			throw new IllegalArgumentException("Transformer " + t + " has already been added for class " + s);
		}
		classTransformers.put(s, t);
	}

	public void addTransformer(@NonNull Transformer t) {
		if (t instanceof Transformer.TargetedTransformer) {
			addTransformer((Transformer.TargetedTransformer) t);
			return;
		}

		if (transformers.contains(t)) {
			throw new IllegalArgumentException("Transformer " + t + " has already been added");
		}
		transformers.add(t);
	}

	public byte[] transformJava(@NonNull byte[] data, @NonNull String name) {
		if (!shouldTransform(name))
			return data;

		CompilationUnit cu;
		try {
			cu = JavaParser.parse(new ByteArrayInputStream(data));
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}

		String packageName = cu.getPackage().getName().getName();
		for (TypeDeclaration typeDeclaration : cu.getTypes()) {
			if (!(typeDeclaration instanceof ClassOrInterfaceDeclaration)) {
				continue;
			}
			val classDeclaration = (ClassOrInterfaceDeclaration) typeDeclaration;

			String shortClassName = classDeclaration.getName();
			if ((packageName + '.' + shortClassName).equalsIgnoreCase(name)) {
				transformJar(new SourceInfo(classDeclaration));
			}
		}

		return cu.toString().getBytes(Charset.forName("UTF-8"));
	}

	public byte[] transformClass(@NonNull byte[] data, @NonNull String name) {
		if (!shouldTransform(name))
			return data;

		ClassNode node = new ClassNode();
		ClassReader reader = new ClassReader(data);
		reader.accept(node, ClassReader.EXPAND_FRAMES);

		transformJar(new ByteCodeInfo(node));

		ClassWriter classWriter = new ClassWriter(reader, 0);
		node.accept(classWriter);
		return classWriter.toByteArray();
	}

	private void transformJar(ClassInfo editor) {
		val name = editor.getName();
		transformers.forEach((x) -> {
			if (x.shouldTransform(name)) {
				x.transform(editor);
			}
		});
		classTransformers.get(name).forEach((it) -> it.transform(editor));
	}

	private boolean shouldTransform(String className) {
		if (!classTransformers.get(className).isEmpty())
			return true;

		for (Transformer transformer : transformers) {
			if (transformer.shouldTransform(className))
				return true;
		}

		return false;
	}

	private Supplier<byte[]> transformBytes(String relativeName, Supplier<byte[]> dataSupplier) {
		if (relativeName.endsWith(".java")) {
			String clazzName = relativeName.substring(0, relativeName.length() - 5).replace('/', '.');
			if (clazzName.startsWith(".")) {
				clazzName = clazzName.substring(1);
			}
			val bytes = transformJava(dataSupplier.get(), clazzName);
			return () -> bytes;
		} else if (relativeName.endsWith(".class")) {
			String clazzName = relativeName.substring(0, relativeName.length() - 6).replace('/', '.');
			if (clazzName.startsWith(".")) {
				clazzName = clazzName.substring(1);
			}
			val bytes = transformClass(dataSupplier.get(), clazzName);
			return () -> bytes;
		}
		return dataSupplier;
	}

	private enum PathType {
		JAR,
		FOLDER;

		static PathType of(Path p) {
			if (!p.getFileName().toString().contains(".")) {
				if (Files.exists(p) && !Files.isDirectory(p)) {
					throw new RuntimeException("Path " + p + " should be a directory or not already exist");
				}
				return FOLDER;
			}
			if (Files.isDirectory(p)) {
				throw new RuntimeException("Path " + p + " should be a file or not already exist");
			}
			return JAR;
		}
	}

	private static class SimpleMultiMap<K, T> {
		private final Map<K, List<T>> map = new HashMap<>();

		public void put(K key, T value) {
			List<T> values = map.get(key);
			if (values == null) {
				values = new ArrayList<>();
				map.put(key, values);
			}
			values.add(value);
		}

		public List<T> get(K key) {
			List<T> values = map.get(key);
			return values == null ? Collections.emptyList() : values;
		}
	}
}
