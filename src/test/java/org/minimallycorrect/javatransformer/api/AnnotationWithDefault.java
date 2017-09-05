package org.minimallycorrect.javatransformer.api;

import org.minimallycorrect.javatransformer.api.code.CodeFragment;

public @interface AnnotationWithDefault {
	String value() default "value";

	int index() default 1;

	CodeFragment.InsertionPosition position() default CodeFragment.InsertionPosition.BEFORE;

	TestEnum testEnum() default TestEnum.FIRST;
}
