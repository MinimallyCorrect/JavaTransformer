package org.minimallycorrect.javatransformer.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import org.minimallycorrect.javatransformer.internal.util.JVMUtil;
import org.minimallycorrect.javatransformer.internal.util.Joiner;

// TODO: make this faster by using dumb regexes instead of JavaParser?
// probably not worth doing
public class ClassPath {
	private final HashSet<String> classes = new HashSet<>();
	private final HashSet<Path> inputPaths = new HashSet<>();
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
		return "[" + parent.toString() + ", " + inputPaths + " classes:\n" + Joiner.on("\n").join(classes.stream().sorted()) + "]";
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
		return classes.contains(className) || (parent != null && parent.classExists(className));
	}

	/**
	 * Adds a {@link Path} to this {@link ClassPath}
	 *
	 * @param path {@link Path} to add
	 * @return true if the path was added, false if the path already existed in this {@link ClassPath}
	 */
	@Contract("null -> fail")
	public boolean addPath(@NonNull Path path) {
		path = path.normalize().toAbsolutePath();
		val add = !parentHasPath(path) && inputPaths.add(path);
		if (add && loaded)
			loadPath(path);
		return add;
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
			findJavaPaths(zis);

		if (entryName.endsWith(".class"))
			classes.add(JVMUtil.fileNameToClassName(entryName));
	}

	private void findJavaPaths(ZipInputStream zis) {
		val parsed = JavaParser.parse(new InputStream() {
			public int read(@NonNull byte[] b, int off, int len) throws IOException {
				return zis.read(b, off, len);
			}

			public void close() throws IOException {}

			public int read() throws IOException {
				return zis.read();
			}
		});
		findJavaPaths(parsed);
	}

	private void findJavaPaths(CompilationUnit compilationUnit) {
		val typeNames = compilationUnit.getTypes();
		val packageDeclaration = compilationUnit.getPackageDeclaration().orElse(null);
		val prefix = packageDeclaration == null ? "" : packageDeclaration.getNameAsString() + '.';
		for (TypeDeclaration<?> typeDeclaration : typeNames)
			findJavaPaths(typeDeclaration, prefix);
	}

	private void findJavaPaths(TypeDeclaration<?> typeDeclaration, String packagePrefix) {
		val name = packagePrefix + typeDeclaration.getNameAsString();
		classes.add(name);
		for (val node : typeDeclaration.getChildNodes())
			if (node instanceof TypeDeclaration)
				findJavaPaths((TypeDeclaration<?>) node, name + '.');
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
						findJavaPaths(parsed);
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
