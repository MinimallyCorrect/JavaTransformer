package org.minimallycorrect.javatransformer.transform;

import java.lang.invoke.MethodHandles;

import org.minimallycorrect.javatransformer.api.code.CodeFragment;

public class InnerClassReferencer {
	public InnerClassExample.Inner test1() {
		throw new UnsupportedOperationException();
	}

	public org.minimallycorrect.javatransformer.transform.InnerClassExample.Inner2 test2() {
		throw new UnsupportedOperationException();
	}

	public AnnotationInnerClassExample.TestEnum test3() {
		return AnnotationInnerClassExample.TestEnum.ONE;
	}

	public org.minimallycorrect.javatransformer.transform.AnnotationInnerClassExample.TestEnum test4() {
		return org.minimallycorrect.javatransformer.transform.AnnotationInnerClassExample.TestEnum.TWO;
	}

	public MethodHandles.Lookup test5() {
		throw new UnsupportedOperationException();
	}

	public java.lang.invoke.MethodHandles.Lookup test6() {
		throw new UnsupportedOperationException();
	}

	public CodeFragment.InsertionOptions test7() {
		return org.minimallycorrect.javatransformer.api.code.CodeFragment.InsertionOptions.DEFAULT;
	}

	public org.minimallycorrect.javatransformer.api.code.CodeFragment.InsertionOptions test8() {
		return CodeFragment.InsertionOptions.DEFAULT;
	}
}
