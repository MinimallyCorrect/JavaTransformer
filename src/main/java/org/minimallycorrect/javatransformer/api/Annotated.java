package org.minimallycorrect.javatransformer.api;

import java.util.List;
import java.util.stream.Collectors;

public interface Annotated {
	List<Annotation> getAnnotations();

	default List<Annotation> getAnnotations(Type t) {
		if (!t.isClassType())
			throw new TransformationException("Type must be a class type: " + t);
		return getAnnotations(t.getClassName());
	}

	default List<Annotation> getAnnotations(String fullAnnotationName) {
		return getAnnotations().stream()
			.filter((it) -> it.getType().getClassName().equals(fullAnnotationName))
			.collect(Collectors.toList());
	}
}
