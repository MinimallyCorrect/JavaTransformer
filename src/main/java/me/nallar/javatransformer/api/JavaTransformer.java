package me.nallar.javatransformer.api;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import lombok.*;
import me.nallar.javatransformer.internal.ByteCodeInfo;
import me.nallar.javatransformer.internal.SourceInfo;
import me.nallar.javatransformer.internal.util.JVMUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.net.*;
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

	/**
	 * Used to get the path of the jar/folder containing a class
	 *
	 * @param clazz Class to get path to
	 * @return Path to class
	 */
	public static Path pathFromClass(Class<?> clazz) {
		try {
			return Paths.get(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (URISyntaxException e) {
			throw new IOError(e);
		}
	}

	public Map<String, List<Transformer>> getClassTransformers() {
		return Collections.unmodifiableMap(classTransformers.map);
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

	public Supplier<byte[]> transformJava(@NonNull Supplier<byte[]> data, @NonNull String name) {
		if (!shouldTransform(name))
			return data;

		CachingSupplier<ClassOrInterfaceDeclaration> supplier = CachingSupplier.of(() -> {
			CompilationUnit cu;
			try {
				cu = JavaParser.parse(new ByteArrayInputStream(data.get()));
			} catch (ParseException e) {
				throw new RuntimeException(e);
			}

			String packageName = cu.getPackage().getName().getName();
			for (TypeDeclaration typeDeclaration : cu.getTypes()) {
				if (!(typeDeclaration instanceof ClassOrInterfaceDeclaration)) {
					continue;
				}
				ClassOrInterfaceDeclaration classDeclaration = (ClassOrInterfaceDeclaration) typeDeclaration;

				String shortClassName = classDeclaration.getName();
				if ((packageName + '.' + shortClassName).equalsIgnoreCase(name)) {
					return classDeclaration;
				}
			}

			throw new Error("Couldn't find any class or interface declaration matching expected name " + name);
		});

		transformClassInfo(new SourceInfo(supplier, name));

		return supplier.isCached() ? () -> supplier.get().toString().getBytes(Charset.forName("UTF-8")) : data;

	}

	public Supplier<byte[]> transformClass(@NonNull Supplier<byte[]> data, @NonNull String name) {
		System.out.println("Transforming " + name);
		if (!shouldTransform(name))
			return data;

		Holder<ClassReader> readerHolder = new Holder<>();
		CachingSupplier<ClassNode> supplier = CachingSupplier.of(() -> {
			ClassNode node = new ClassNode();
			ClassReader reader = new ClassReader(data.get());
			reader.accept(node, ClassReader.EXPAND_FRAMES);

			readerHolder.value = reader;

			return node;
		});

		transformClassInfo(new ByteCodeInfo(supplier, name));

		if (!supplier.isCached())
			return data;

		return () -> {
			ClassWriter classWriter = new ClassWriter(readerHolder.value, 0);
			supplier.get().accept(classWriter);
			return classWriter.toByteArray();
		};
	}

	private void transformClassInfo(ClassInfo editor) {
		transformers.forEach((x) -> {
			x.transform(editor);
		});
		classTransformers.get(editor.getName()).forEach((it) -> it.transform(editor));
	}

	private boolean shouldTransform(String className) {
		return !transformers.isEmpty() || !classTransformers.get(className).isEmpty();
	}

	private Supplier<byte[]> transformBytes(String relativeName, Supplier<byte[]> dataSupplier) {
		boolean isClass = relativeName.endsWith(".class");
		boolean isSource = relativeName.endsWith(".java");

		if (isClass || isSource) {
			String className = JVMUtil.fileNameToClassName(relativeName);

			if (isClass)
				return transformClass(dataSupplier, className);

			return transformJava(dataSupplier, className);
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

		public String toString() {
			return map.toString();
		}
	}

	@Data
	private static class CachingSupplier<T> implements Supplier<T> {
		@NonNull
		private final Supplier<T> wrapped;
		private transient T value;

		protected CachingSupplier(Supplier<T> wrapped) {
			this.wrapped = wrapped;
		}

		public static <T> CachingSupplier<T> of(Supplier<T> wrapped) {
			return new CachingSupplier<>(wrapped);
		}

		@Override
		public T get() {
			T value = this.value;

			if (value == null) {
				synchronized (this) {
					if (this.value == null)
						this.value = value = Objects.requireNonNull(wrapped.get());
				}
			}

			return value;
		}

		public boolean isCached() {
			return value != null;
		}
	}

	private static class Holder<T> {
		public T value;
	}
}
