package org.minimallycorrect.javatransformer.api;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.minimallycorrect.javatransformer.internal.util.JVMUtil;
import org.minimallycorrect.javatransformer.internal.util.Joiner;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.zip.*;

// TODO: make this faster by using dumb regexes instead of JavaParser?
// probably not worth doing
public class ClassPath {
	private final Map<String, String> classNameToPath = new HashMap<>();
	private final Set<Path> inputPaths = new HashSet<>();
	private final ClassPath parent;
	private boolean loaded;

	private ClassPath(@Nullable ClassPath parent) {
		this.parent = parent;
	}

	public ClassPath() {
		this((ClassPath) null);
	}

	public ClassPath(Collection<Path> paths) {
		this();
		addPaths(paths);
	}

	public ClassPath createChildWithExtraPaths(Collection<Path> paths) {
		val searchPath = new ClassPath(this);
		searchPath.addPaths(paths);
		return searchPath;
	}

	@Override
	public String toString() {
		initialise();
		return "[" + parent.toString() + ", " + inputPaths + " classes:\n" + Joiner.on("\n").join(classNameToPath.keySet().stream().sorted()) + "]";
	}

	/**
	 * Returns whether the given class name exists in this class path
	 *
	 * @param className class name in JLS format: package1.package2.ClassName, package1.package2.ClassName$InnerClass
	 * @return true if the class exists
	 */
	@Contract("null -> fail")
	public boolean classExists(@NonNull String className) {
		initialise();
		return classNameToPath.containsKey(className) || (parent != null && parent.classExists(className));
	}

	@Contract("null -> fail")
	public void addPath(@NonNull Path path) {
		path = path.normalize().toAbsolutePath();
		if (inputPaths.add(path) && loaded)
			loadPath(path);
	}

	@Contract("null -> fail")
	public void addPaths(@NonNull Collection<Path> paths) {
		for (Path path : paths)
			addPath(path);
	}

	/**
	 * @param path path must be normalized and absolute
	 */
	private boolean parentHasPath(Path path) {
		val parent = this.parent;
		return parent != null && (parent.inputPaths.contains(path) || parent.parentHasPath(path));
	}

	private void findPaths(ZipEntry e, ZipInputStream zis) {
		val entryName = e.getName();
		if (entryName.endsWith(".java"))
			findJavaPaths(entryName, zis);

		if (entryName.endsWith(".class"))
			classNameToPath.put(JVMUtil.fileNameToClassName(entryName), entryName);
	}

	private void findJavaPaths(String entryName, ZipInputStream zis) {
		val parsed = JavaParser.parse(new InputStream() {
			public int read(byte[] b, int off, int len) throws IOException {
				return zis.read(b, off, len);
			}

			public void close() throws IOException {
			}

			public int read() throws IOException {
				return zis.read();
			}
		});
		findJavaPaths(entryName, parsed);
	}

	private void findJavaPaths(String path, CompilationUnit compilationUnit) {
		val typeNames = compilationUnit.getTypes();
		val packageDeclaration = compilationUnit.getPackageDeclaration().orElse(null);
		val prefix = packageDeclaration == null ? "" : packageDeclaration.getNameAsString() + '.';
		for (TypeDeclaration<?> typeDeclaration : typeNames)
			findJavaPaths(path, typeDeclaration, prefix);
	}

	private void findJavaPaths(String path, TypeDeclaration<?> typeDeclaration, String packagePrefix) {
		val name = packagePrefix + typeDeclaration.getNameAsString();
		classNameToPath.put(name, path);
		for (val node : typeDeclaration.getChildNodes())
			if (node instanceof TypeDeclaration)
				findJavaPaths(path, (TypeDeclaration<?>) node, name + '.');
	}

	private void initialise() {
		if (loaded)
			return;
		loaded = true;
		for (Path path : inputPaths)
			loadPath(path);
	}

	@SneakyThrows
	private void loadPath(Path path) {
		if (Files.isDirectory(path))
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					val entryName = path.relativize(file).toString().replace(File.separatorChar, '/');
					if (entryName.endsWith(".java")) {
						val parsed = JavaParser.parse(file);
						findJavaPaths(entryName, parsed);
					}
					return super.visitFile(file, attrs);
				}
			});
		else if (Files.isRegularFile(path))
			try (val zis = new ZipInputStream(Files.newInputStream(path))) {
				ZipEntry e;
				while ((e = zis.getNextEntry()) != null) {
					try {
						findPaths(e, zis);
					} finally {
						zis.closeEntry();
					}
				}
			}
	}
}
