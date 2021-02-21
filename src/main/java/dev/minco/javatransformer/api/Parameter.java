package dev.minco.javatransformer.api;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(exclude = {"annotationSupplier", "name"})
@Getter
public class Parameter implements Annotated {
	@Nullable
	public final String name;
	@NonNull
	public final Type type;
	@Getter(AccessLevel.NONE)
	private final Supplier<List<Annotation>> annotationSupplier;

	private Parameter(@NonNull Type type, @Nullable String name, Supplier<List<Annotation>> annotationSupplier) {
		this.type = type;
		this.name = name;
		this.annotationSupplier = annotationSupplier;
	}

	public static Parameter of(Type type, String name, @Nullable Supplier<List<Annotation>> annotationSupplier) {
		return new Parameter(type, name, annotationSupplier);
	}

	@Override
	public List<Annotation> getAnnotations() {
		if (annotationSupplier == null)
			return Collections.emptyList();
		return annotationSupplier.get();
	}

	public String toString() {
		return "Parameter(name=" + this.getName() + ", type=" + this.getType() + ")";
	}
}
