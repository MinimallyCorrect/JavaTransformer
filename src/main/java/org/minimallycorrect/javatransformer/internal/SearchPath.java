package org.minimallycorrect.javatransformer.internal;

import lombok.SneakyThrows;
import lombok.val;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class SearchPath {
	final List<Path> directories = new ArrayList<>();
	final Set<String> paths = new HashSet<>();
	final List<Path> inputPaths;

	@SneakyThrows
	public SearchPath(List<Path> paths) {
		inputPaths = paths;
		for (Path path : paths)
			if (Files.isDirectory(path))
				directories.add(path);
			else if (Files.isRegularFile(path))
				try (val zis = new ZipInputStream(Files.newInputStream(path))) {
					ZipEntry e;
					while ((e = zis.getNextEntry()) != null) {
						val name = e.getName().toLowerCase();
						if (name.endsWith(".class") || name.endsWith(".java"))
							this.paths.add(e.getName());
					}
				}

	}

	@Override
	public String toString() {
		return "SearchPath: " + inputPaths;
	}

	public boolean exists(String s) {
		String name = s.replace('.', '/') + ".java";
		if (paths.contains(name))
			return true;
		for (Path directory : directories)
			if (Files.exists(directory.resolve(name)))
				return true;
		return false;
	}
}
