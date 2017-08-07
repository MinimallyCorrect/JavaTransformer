package org.minimallycorrect.javatransformer.api;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

import java.util.*;
import java.util.function.*;

@Getter
@ToString
public class Parameter implements Annotated {
	public final String name;
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
