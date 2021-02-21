package dev.minco.javatransformer.transform;

import java.lang.invoke.MethodHandles;

import dev.minco.javatransformer.api.code.CodeFragment;

public class InnerClassReferencer {
	public InnerClassExample.Inner test1() {
		throw new UnsupportedOperationException();
	}

	public InnerClassExample.Inner2 test2() {
		throw new UnsupportedOperationException();
	}

	public AnnotationInnerClassExample.TestEnum test3() {
		return AnnotationInnerClassExample.TestEnum.ONE;
	}

	public AnnotationInnerClassExample.TestEnum test4() {
		return AnnotationInnerClassExample.TestEnum.TWO;
	}

	public MethodHandles.Lookup test5() {
		throw new UnsupportedOperationException();
	}

	public java.lang.invoke.MethodHandles.Lookup test6() {
		throw new UnsupportedOperationException();
	}

	public CodeFragment.InsertionOptions test7() {
		return CodeFragment.InsertionOptions.DEFAULT;
	}

	public CodeFragment.InsertionOptions test8() {
		return CodeFragment.InsertionOptions.DEFAULT;
	}
}
