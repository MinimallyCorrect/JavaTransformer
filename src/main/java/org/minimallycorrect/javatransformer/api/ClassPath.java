package org.minimallycorrect.javatransformer.api;

import java.nio.file.Path;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import org.minimallycorrect.javatransformer.internal.ClassPaths;

public interface ClassPath extends Iterable<ClassInfo> {
	/**
	 * Returns whether the given class name exists
	 *
	 * @param className class name in JLS format: {@code package1.package2.ClassName}, {@code package1.package2.ClassName$InnerClass}
	 * @return true if the class exists
	 */
	@Contract(value = "null -> fail", pure = true)
	default boolean classExists(@Nonnull String className) {
		return getClassInfo(className) != null;
	}

	/**
	 * Returns the class info for the given class
	 *
	 * This instance is only guaranteed to provide information required for type resolution. Other information such as method bodies and annotations may be stripped
	 *
	 * @param className class name in JLS format: {@code package1.package2.ClassName}, {@code package1.package2.ClassName$InnerClass}
	 * @return ClassInfo for the given name, or null if not found
	 */
	@Contract(value = "null -> fail", pure = true)
	@Nullable
	ClassInfo getClassInfo(@Nonnull String className);

	/**
	 * Adds the given paths to this {@link ClassPath}
	 *
	 * @param paths Paths to add
	 */
	default void addPaths(List<Path> paths) {
		for (Path extraPath : paths)
			addPath(extraPath);
	}

	/**
	 * Adds a path to this {@link ClassPath}
	 *
	 * @param path Path to add
	 * @return True if the path was added, false if it was already in this classpath
	 * @throws UnsupportedOperationException if this {@link ClassPath} is immutable
	 */
	boolean addPath(Path path);

	/**
	 * Checks if the given path is in this {@link ClassPath}
	 *
	 * @param path Path to check
	 * @return True if this {@link ClassPath} contains the given path
	 */
	boolean hasPath(Path path);

	@Contract(pure = true)
	static @Nonnull ClassPath of(@Nonnull Path... paths) {
		return of(ClassPaths.SystemClassPath.SYSTEM_CLASS_PATH, paths);
	}

	@Contract(pure = true)
	static @Nonnull ClassPath of(@Nullable ClassPath parent, Path... paths) {
		return ClassPaths.of(parent, paths);
	}
}
