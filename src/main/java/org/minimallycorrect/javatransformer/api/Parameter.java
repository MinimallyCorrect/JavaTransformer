package org.minimallycorrect.javatransformer.api;

import lombok.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

@EqualsAndHashCode(exclude = {"annotationSupplier"})
@Getter
@ToString
public class Parameter implements Annotated {
	@Nullable
	public final String name;
	@NonNull
	public final Type type;
	@Getter(AccessLevel.NONE)
	private final Supplier<List<Annotation>> annotationSupplier;

	public Parameter(Type type, String name) {
		this(type, name, null);
	}

	public Parameter(Type type, String name, Supplier<List<Annotation>> annotationSupplier) {
		this.type = type;
		this.name = name;
		this.annotationSupplier = annotationSupplier;
	}

	@Override
	public List<Annotation> getAnnotations() {
		if (annotationSupplier == null)
			return null;
		return annotationSupplier.get();
	}
}
