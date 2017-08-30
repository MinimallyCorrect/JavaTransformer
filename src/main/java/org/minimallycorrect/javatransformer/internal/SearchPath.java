package org.minimallycorrect.javatransformer.internal;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import lombok.SneakyThrows;
import lombok.val;
import org.jetbrains.annotations.Nullable;
import org.minimallycorrect.javatransformer.internal.util.Joiner;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.zip.*;

// TODO: make this faster by using dumb regexes instead of JavaParser?
// probably not worth doing
public class SearchPath {
	final Map<String, String> classNameToPath = new HashMap<>();
	final List<Path> inputPaths;

	@SneakyThrows
	public SearchPath(List<Path> paths) {
		inputPaths = paths;
		for (Path path : paths)
			if (Files.isDirectory(path))
				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						val entryName = path.relativize(file).toString().replace(File.separatorChar, '/');
						if (entryName.endsWith(".java")) {
							val parsed = JavaParser.parse(file);
							findPaths(entryName, parsed);
						}
						return super.visitFile(file, attrs);
					}
				});
			else if (Files.isRegularFile(path))
				try (val zis = new ZipInputStream(Files.newInputStream(path))) {
					ZipEntry e;
					while ((e = zis.getNextEntry()) != null) {
						findPaths(e, zis);
						zis.closeEntry();
					}
				}
	}

	private void findPaths(ZipEntry e, ZipInputStream zis) {
		val entryName = e.getName();
		if (!e.getName().endsWith(".java"))
			return;
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
		findPaths(entryName, parsed);
	}

	private void findPaths(String path, CompilationUnit compilationUnit) {
		val typeNames = compilationUnit.getTypes();
		val packageDeclaration = compilationUnit.getPackageDeclaration().orElse(null);
		val prefix = packageDeclaration == null ? "" : packageDeclaration.getNameAsString() + '.';
		for (TypeDeclaration<?> typeDeclaration : typeNames)
			findPaths(path, typeDeclaration, prefix);
	}

	private void findPaths(String path, TypeDeclaration<?> typeDeclaration, String packagePrefix) {
		val parent = typeDeclaration.getParentNode().orElse(null);
		val name = packagePrefix + typeDeclaration.getNameAsString();
		classNameToPath.put(name, path);
		for (val node : typeDeclaration.getChildNodes())
			if (node instanceof TypeDeclaration)
				findPaths(path, (TypeDeclaration<?>) node, name + '.');
	}

	@Override
	public String toString() {
		return "SearchPath: " + inputPaths + " classes:\n" + Joiner.on("\n").join(classNameToPath.keySet().stream().sorted());
	}

	public boolean hasClass(String className) {
		return classNameToPath.containsKey(className);
	}

	@Nullable
	public String getPathForClass(String className) {
		return classNameToPath.get(className);
	}
}
