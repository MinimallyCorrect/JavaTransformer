package me.nallar.javatransformer.internal.util;

import java.util.*;

public interface Splitter {
	static Splitter on(char c) {
		return s -> {
			ArrayList<String> split = new ArrayList<>();
			int base = 0;
			do {
				int next = s.indexOf(c, base);

				int nextBase = next + 1;

				if (next == -1)
					next = s.length();

				String part = s.substring(base, next).trim();

				if (!part.isEmpty())
					split.add(part);

				base = nextBase;
			} while (base != 0);
			return split;
		};
	}

	Iterable<String> split(String s);
}
