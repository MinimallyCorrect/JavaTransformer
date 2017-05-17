package org.minimallycorrect.javatransformer.internal.util;

import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

@UtilityClass
public class CollectionUtil {
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <T> Stream<T> union(Collection<T>... collections) {
		return union(Arrays.asList(collections));
	}

	public static <T> Stream<T> union(Collection<Collection<T>> collections) {
		return collections.stream().flatMap(x -> x == null ? Stream.empty() : x.stream());
	}

	public static <T> Stream<T> stream(Supplier<T> supplier) {
		return stream(iterable(supplier));
	}

	public static <T> Stream<T> stream(Iterable<T> iterable) {
		if (iterable instanceof Collection)
			return ((Collection<T>) iterable).stream();

		return StreamSupport.stream(iterable.spliterator(), false);
	}

	public static <T> Iterable<T> iterable(Stream<T> supplier) {
		return supplier::iterator;
	}

	public static <T> Iterable<T> iterable(Supplier<T> supplier) {
		return () -> new IteratorFromSupplier<>(supplier);
	}

	private static class IteratorFromSupplier<T> implements Iterator<T> {
		private final Supplier<T> supplier;
		private T next;

		public IteratorFromSupplier(Supplier<T> supplier) {
			this.supplier = supplier;
			next = supplier.get();
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public T next() {
			if (next == null)
				throw new NoSuchElementException();

			try {
				return next;
			} finally {
				next = supplier.get();
			}
		}
	}
}
