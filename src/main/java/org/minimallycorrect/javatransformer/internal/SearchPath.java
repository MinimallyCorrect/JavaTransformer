package org.minimallycorrect.javatransformer.internal;

import lombok.SneakyThrows;
import lombok.ToString;
import lombok.val;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

@ToString
public class SearchPath {
	final List<Path> directories = new ArrayList<>();
	final Set<String> paths = new HashSet<>();

	@SneakyThrows
	public SearchPath(Iterable<Path> paths) {
		for (Path path : paths)
			if (Files.isDirectory(path))
				directories.add(path);
			else if (Files.isRegularFile(path))
				try (val zis = new ZipInputStream(Files.newInputStream(path))) {
					ZipEntry e;
					while ((e = zis.getNextEntry()) != null)
						this.paths.add(e.getName());
				}

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
