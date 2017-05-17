package org.minimallycorrect.javatransformer.internal.util;

import lombok.Data;
import lombok.NonNull;

import java.util.*;
import java.util.function.*;

@Data
public class CachingSupplier<T> implements Supplier<T> {
	@NonNull
	private final Supplier<T> wrapped;
	private transient T value;

	protected CachingSupplier(Supplier<T> wrapped) {
		this.wrapped = wrapped;
	}

	public static <T> CachingSupplier<T> of(Supplier<T> wrapped) {
		return new CachingSupplier<>(wrapped);
	}

	@Override
	public T get() {
		T value = this.value;

		if (value == null) {
			synchronized (this) {
				if (this.value == null)
					this.value = value = Objects.requireNonNull(wrapped.get());
			}
		}

		return value;
	}

	public void set(@NonNull T value) {
		this.value = value;
	}

	public boolean isCached() {
		return value != null;
	}
}
