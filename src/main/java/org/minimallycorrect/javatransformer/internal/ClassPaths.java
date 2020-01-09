package org.minimallycorrect.javatransformer.internal;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;

import org.jetbrains.annotations.NotNull;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserAnnotationDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserEnumDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;

import org.minimallycorrect.javatransformer.api.*;
import org.minimallycorrect.javatransformer.internal.asm.AsmUtil;
import org.minimallycorrect.javatransformer.internal.javaparser.AsmResolvedTypes;
import org.minimallycorrect.javatransformer.internal.javaparser.CompilationUnitInfo;
import org.minimallycorrect.javatransformer.internal.util.*;

@UtilityClass
public class ClassPaths {
	public static ClassPath of(ClassPath systemClassPath, Path... paths) {
		return new FileClassPath(systemClassPath, new ArrayList<>(Arrays.asList(paths)));
	}

	public static class SystemClassPath {
		public static final ClassPath SYSTEM_CLASS_PATH = makeSystemJarClassPath();

		private static ClassPath makeSystemJarClassPath() {
			// only scan java/ files in boot class path
			// avoid JVM/JDK internals
			// TODO: self-test, if we can't load JDK classes with current asm version fall back to reflection
			try {
				val paths = Splitter.pathSplitter.split(ManagementFactory.getRuntimeMXBean().getBootClassPath())
					.map(it -> Paths.get(it)).filter(it -> it.getFileName().toString().equals("rt.jar"))
					.collect(Collectors.toList());
				return new FileClassPath(null, paths);
			} catch (UnsupportedOperationException ignored) {
				val fs = FileSystems.getFileSystem(URI.create("jrt:/"));
				return new FileClassPath(null, Collections.singletonList(fs.getPath("modules/java.base/")));
			}
		}
	}

	private static abstract class ClassPathSolver implements ClassPath, TypeSolver {
		@Nullable
		final ClassPath parent;

		ClassPathSolver(@Nullable ClassPath parent) {
			this.parent = parent;
		}

		@Override
		public TypeSolver getParent() {
			return parent instanceof TypeSolver ? (TypeSolver) parent : null;
		}

		@Override
		public void setParent(TypeSolver parent) {
			throw new UnsupportedOperationException("TODO");
		}

		static Path normalise(Path path) {
			return path.toAbsolutePath().normalize();
		}

		@NonNull
		@Override
		public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
			val ci = getClassInfo(name);
			if (ci == null) {
				return SymbolReference.unsolved(ResolvedReferenceTypeDeclaration.class);
			}
			return SymbolReference.solved(getResolvedReferenceTypeDeclarationForClassInfo(ci));
		}

		private ResolvedReferenceTypeDeclaration getResolvedReferenceTypeDeclarationForClassInfo(ClassInfo ci) {
			if (ci instanceof SourceInfo) {
				val jpType = ((SourceInfo) ci).getJavaParserType();
				if (jpType.isClassOrInterfaceDeclaration()) {
					return new JavaParserClassDeclaration(jpType.asClassOrInterfaceDeclaration(), this);
				} else if (jpType.isEnumDeclaration()) {
					return new JavaParserEnumDeclaration(jpType.asEnumDeclaration(), this);
				} else if (jpType.isAnnotationDeclaration()) {
					return new JavaParserAnnotationDeclaration(jpType.asAnnotationDeclaration(), this);
				}
			}
			if (ci instanceof ByteCodeInfo) {
				return AsmResolvedTypes.fromByteCodeInfo(this, (ByteCodeInfo) ci);
			}
			throw new UnsupportedOperationException("TODO " + ci);
		}
	}

	static class FileClassPath extends ClassPathSolver {
		private final Map<String, ClassInfo> entries = new HashMap<>();
		private final Collection<Path> paths;
		private boolean initialised;

		public FileClassPath(@Nullable ClassPath parent, Collection<Path> paths) {
			super(parent);
			this.paths = paths;
		}

		@Nullable
		@Override
		public ClassInfo getClassInfo(@Nonnull String className) {
			Objects.requireNonNull(className);
			if (parent != null) {
				val p = parent.getClassInfo(className);
				if (p != null)
					return p;
			}
			if (!initialised)
				initialise();
			return entries.get(className);
		}

		@Override
		public synchronized boolean addPath(Path path) {
			path = normalise(path);
			if (hasPath(path)) {
				return false;
			}
			paths.add(path);
			if (initialised) {
				loadPath(path);
			}
			return true;
		}

		@Override
		public boolean hasPath(Path path) {
			path = normalise(path);
			return paths.contains(path) || (parent != null && parent.hasPath(path));
		}

		@NotNull
		@Override
		public Iterator<ClassInfo> iterator() {
			initialise();
			if (parent == null) {
				return entries.values().iterator();
			}
			return CollectionUtil.union(parent, entries.values());
		}

		@SneakyThrows
		private void findPaths(String entryName, Supplier<InputStream> iss) {
			if (entryName.endsWith(".java"))
				try (val is = iss.get()) {
					findJavaPaths(is);
				}

			if (entryName.endsWith(".class")) {
				String name = JVMUtil.fileNameToClassName(entryName);
				entries.put(name, new ByteCodeInfo(() -> AsmUtil.getClassNode(StreamUtil.readFully(iss.get()), null), name, Collections.emptyMap()));
			}
		}

		private void findJavaPaths(InputStream is) {
			val parsed = JavaParser.parse(is);
			findJavaPaths(parsed);
		}

		private void findJavaPaths(CompilationUnit compilationUnit) {
			for (ClassInfo classInfo : CompilationUnitInfo.getSourceInfos(compilationUnit, this))
				entries.put(classInfo.getName(), classInfo);
		}

		private synchronized void initialise() {
			if (!initialised) {
				for (Path path : paths)
					loadPath(path);
				initialised = true;
			}
		}

		@SneakyThrows
		private void loadPath(Path path) {
			if (Files.isDirectory(path))
				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
					@SneakyThrows
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						val entryName = path.relativize(file).toString().replace(File.separatorChar, '/');
						findPaths(entryName, () -> {
							try {
								return Files.newInputStream(file);
							} catch (IOException e) {
								throw new IOError(e);
							}
						});
						return super.visitFile(file, attrs);
					}
				});
			else if (Files.isRegularFile(path))
				try (val zf = new ZipFile(path.toFile())) {
					val e$ = zf.entries();
					while (e$.hasMoreElements()) {
						val ze = e$.nextElement();
						val name = ze.getName();
						findPaths(name, () -> {
							try {
								val zff = new ZipFile(path.toFile());
								return zff.getInputStream(zff.getEntry(name));
							} catch (IOException e) {
								throw new IOError(e);
							}
						});
					}
				}
		}

		@Override
		public String toString() {
			return "FileClassPath{" +
				"entries.size()=" + entries.size() +
				", paths=" + paths +
				", initialised=" + initialised +
				", parent=" + parent +
				'}';
		}
	}

	/*
	@Getter
	@Setter
	private static class SimpleClassInfo implements ClassInfo {
		private Type superType;
		private List<Type> interfaceTypes;
		private String name;
		private AccessFlags accessFlags = new AccessFlags(0);
		private List<MethodInfo> methods = new ArrayList<>();
		private List<FieldInfo> fields = new ArrayList<>();
		private List<Annotation> annotations = new ArrayList<>();
	
		@Override
		public void add(MethodInfo method) {
			methods.add(method);
		}
	
		@Override
		public void add(FieldInfo field) {
			fields.add(field);
		}
	
		@Override
		public void remove(MethodInfo method) {
			methods.remove(method);
		}
	
		@Override
		public void remove(FieldInfo field) {
			fields.remove(field);
		}
	}
	*/
}
