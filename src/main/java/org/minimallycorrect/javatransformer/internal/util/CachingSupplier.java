package org.minimallycorrect.javatransformer.internal.util;

import java.util.Objects;
import java.util.function.Supplier;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode
@ToString
public final class CachingSupplier<T> implements Supplier<T> {
	@NonNull
	private final Supplier<T> wrapped;
	private transient T value;

	private CachingSupplier(Supplier<T> wrapped) {
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

	public void set(@Nullable T value) {
		this.value = value;
	}

	public boolean isCached() {
		return value != null;
	}
}
