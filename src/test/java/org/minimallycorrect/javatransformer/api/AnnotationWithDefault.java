package org.minimallycorrect.javatransformer.api;

public @interface AnnotationWithDefault {
	String value() default "value";

	int index() default 1;
}
