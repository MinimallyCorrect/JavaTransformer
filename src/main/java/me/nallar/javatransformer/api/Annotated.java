package me.nallar.javatransformer.api;

import java.util.*;
import java.util.stream.*;

public interface Annotated {
	List<Annotation> getAnnotations();

	default List<Annotation> getAnnotations(Type t) {
		if (!t.isClassType())
			throw new RuntimeException("Type must be a class type: " + t);
		return getAnnotations(t.getClassName());
	}

	default List<Annotation> getAnnotations(String fullAnnotationName) {
		return getAnnotations().stream()
			.filter((it) -> it.getType().getClassName().equals(fullAnnotationName))
			.collect(Collectors.toList());
	}
}
