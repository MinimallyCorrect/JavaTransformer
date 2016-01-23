package me.nallar.javatransformer.internal.util;

import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.stream.*;

@UtilityClass
public class CollectionUtil {
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <T> Stream<T> union(Collection<T>... collections) {
		return union(Arrays.asList(collections));
	}

	public static <T> Stream<T> union(Collection<Collection<T>> collections) {
		return collections.stream().flatMap(Collection::stream);
	}
}
