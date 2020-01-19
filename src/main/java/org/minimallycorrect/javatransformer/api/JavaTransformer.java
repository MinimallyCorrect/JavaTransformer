package org.minimallycorrect.javatransformer.api;

import static com.github.javaparser.Providers.provider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import lombok.NonNull;
import lombok.ToString;
import lombok.val;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseStart;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import org.minimallycorrect.javatransformer.internal.ByteCodeInfo;
import org.minimallycorrect.javatransformer.internal.SourceInfo;
import org.minimallycorrect.javatransformer.internal.asm.AsmUtil;
import org.minimallycorrect.javatransformer.internal.asm.FilteringClassWriter;
import org.minimallycorrect.javatransformer.internal.util.CachingSupplier;
import org.minimallycorrect.javatransformer.internal.util.DefineClass;
import org.minimallycorrect.javatransformer.internal.util.JVMUtil;
import org.minimallycorrect.javatransformer.internal.util.NodeUtil;
import org.minimallycorrect.javatransformer.internal.util.StreamUtil;

@ToString
public class JavaTransformer {
	private final List<Transformer> transformers = new ArrayList<>();
	private final SimpleMultiMap<String, Transformer> classTransformers = new SimpleMultiMap<>();
	private final Map<String, byte[]> transformedFiles = new HashMap<>();
	private final List<Consumer<JavaTransformer>> afterTransform = new ArrayList<>();
	private ClassPath classPath = ClassPath.of();

	/**
	 * Used to get the path of the jar/folder containing a class
	 *
	 * @param clazz Class to get path to
	 * @return Path to class
	 */
	public static Path pathFromClass(Class<?> clazz) {
		URL location;
		try {
			location = clazz.getProtectionDomain().getCodeSource().getLocation().toURI().toURL();
		} catch (MalformedURLException | URISyntaxException e) {
			throw new TransformationException(e);
		}
		try {
			if (location.getProtocol().equals("jar")) {
				String path = location.getPath();
				int bang = path.lastIndexOf('!');
				location = new URL(bang == -1 ? path : path.substring(0, bang));
			}

			return Paths.get(location.toURI());
		} catch (Exception e) {
			throw new TransformationException("Failed to get pathFromClass, location: " + location, e);
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
		afterTransform.forEach(handler -> handler.accept(this));
	}

	public void transform(@NonNull Path load, @NonNull Path save) {
		load(load, true);
		save(save);

		clear();
	}

	private void loadFolder(Path input, boolean saveTransformedResults) {
		try {
			val searchPath = ClassPath.of(classPath, input);
			Files.walkFileTree(input, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					val relativeName = input.relativize(file).toString();

					val supplier = transformBytes(() -> {
						try {
							return Files.readAllBytes(file);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					}, relativeName, searchPath);

					saveTransformedResult(relativeName, supplier, saveTransformedResults);

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void loadJar(Path p, boolean saveTransformedResults) {
		ZipEntry entry;
		try (ZipInputStream is = new ZipInputStream(new BufferedInputStream(new FileInputStream(p.toFile())))) {
			val searchPath = ClassPath.of(classPath, p);
			while ((entry = is.getNextEntry()) != null) {
				saveTransformedResult(entry.getName(), transformBytes(() -> StreamUtil.readFully(is), entry.getName(), searchPath), saveTransformedResults);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void saveTransformedResult(String relativeName, Supplier<byte[]> supplier, boolean saveTransformedResults) {
		if (saveTransformedResults)
			transformedFiles.put(relativeName.replace('\\', '/'), supplier.get());
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
				throw new UncheckedIOException(e);
			}
		}));
	}

	private void saveJar(Path jar) {
		try (ZipOutputStream os = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(jar.toFile())))) {
			transformedFiles.forEach(((relativeName, bytes) -> {
				try {
					os.putNextEntry(new ZipEntry(relativeName));
					os.write(bytes);
					os.closeEntry();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
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

	public Supplier<byte[]> transformJava(@NonNull Supplier<byte[]> data, @NonNull String name, ClassPath classPath) {
		if (!shouldTransform(name))
			return data;

		CachingSupplier<TypeDeclaration<?>> supplier = CachingSupplier.of(() -> {
			byte[] bytes = data.get();

			val parser = new JavaParser();
			val result = parser.parse(ParseStart.COMPILATION_UNIT, provider(new ByteArrayInputStream(bytes), Charset.forName("UTF8")));
			if (!result.isSuccessful()) {
				throw new ParseProblemException(result.getProblems());
			}
			CompilationUnit cu = result.getResult().get();

			List<String> tried = new ArrayList<>();
			String packageName = NodeUtil.qualifiedName(cu.getPackageDeclaration().get().getName());
			for (TypeDeclaration<?> typeDeclaration : cu.getTypes()) {
				String shortClassName = typeDeclaration.getName().asString();
				String fullName = packageName + '.' + shortClassName;
				if (fullName.equalsIgnoreCase(name)) {
					return typeDeclaration;
				}

				tried.add(fullName);
			}

			throw new Error("Couldn't find any class or interface declaration matching expected name " + name
				+ "\nTried: " + tried
				+ "\nClass data: " + new String(bytes, Charset.forName("UTF-8")));
		});

		transformClassInfo(new SourceInfo(supplier, name, classPath));

		return supplier.isCached() ? () -> supplier.get().getParentNode().get().toString().getBytes(Charset.forName("UTF-8")) : data;
	}

	public Supplier<byte[]> transformClass(@NonNull Supplier<byte[]> data, @NonNull String name) {
		if (!shouldTransform(name))
			return data;

		AsmUtil.Holder<ClassReader> readerHolder = new AsmUtil.Holder<>();
		CachingSupplier<ClassNode> supplier = CachingSupplier.of(() -> AsmUtil.getClassNode(data.get(), readerHolder));

		val filters = new HashMap<String, String>();

		int flags_ = ClassWriter.COMPUTE_MAXS;
		{
			val byteCodeInfo = new ByteCodeInfo(supplier, name, filters);
			transformClassInfo(byteCodeInfo);
			if (byteCodeInfo.hasChangedMethodControlFlow) {
				flags_ |= ClassWriter.COMPUTE_FRAMES;
			}
		}
		final int flags = flags_;

		if (!supplier.isCached())
			return data;

		return () -> {
			if (readerHolder.value == null)
				throw new IllegalStateException();
			FilteringClassWriter classWriter = new FilteringClassWriter(readerHolder.value, flags);
			classWriter.filters.putAll(filters);
			supplier.get().accept(classWriter);
			return classWriter.toByteArray();
		};
	}

	private void transformClassInfo(ClassInfo editor) {
		transformers.forEach((x) -> x.transform(editor));
		classTransformers.get(editor.getName()).forEach((it) -> it.transform(editor));
	}

	private boolean shouldTransform(String className) {
		return !transformers.isEmpty() || !classTransformers.get(className).isEmpty();
	}

	Supplier<byte[]> transformBytes(@Nullable Supplier<byte[]> dataSupplier, String relativeName, @Nullable ClassPath classPath) {
		boolean isClass = relativeName.endsWith(".class");
		boolean isSource = relativeName.endsWith(".java");

		if (isClass || isSource) {
			String className = JVMUtil.fileNameToClassName(relativeName);

			// package-info files do not contain classes
			if (className.endsWith(".package-info"))
				return dataSupplier;

			if (isClass)
				return transformClass(dataSupplier, className);

			return transformJava(dataSupplier, className, classPath);
		}

		return dataSupplier;
	}

	public Class<?> defineClass(ClassLoader classLoader, String name) {
		byte[] result = Objects.requireNonNull(transformedFiles.get(JVMUtil.classNameToFileName(name)));
		return DefineClass.defineClass(classLoader, name, result);
	}

	public List<Transformer> getTransformers() {
		return this.transformers;
	}

	public Map<String, byte[]> getTransformedFiles() {
		return this.transformedFiles;
	}

	public List<Consumer<JavaTransformer>> getAfterTransform() {
		return this.afterTransform;
	}

	public ClassPath getClassPath() {
		return this.classPath;
	}

	public void setClassPath(ClassPath classPath) {
		this.classPath = classPath;
	}

	private enum PathType {
		JAR,
		FOLDER;

		static PathType of(Path p) {
			if (!p.getFileName().toString().contains(".")) {
				if (Files.exists(p) && !Files.isDirectory(p)) {
					throw new TransformationException("Path " + p + " should be a directory or not already exist");
				}
				return FOLDER;
			}
			if (Files.isDirectory(p)) {
				throw new TransformationException("Path " + p + " should be a file or not already exist");
			}
			return JAR;
		}
	}

	private static class SimpleMultiMap<K, T> {
		private final Map<K, List<T>> map = new HashMap<>();

		public void put(K key, T value) {
			map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
		}

		public List<T> get(K key) {
			List<T> values = map.get(key);
			return values == null ? Collections.emptyList() : values;
		}

		public String toString() {
			return map.toString();
		}
	}

}
