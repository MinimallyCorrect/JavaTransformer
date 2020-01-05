package org.minimallycorrect.javatransformer.transform;

import java.lang.invoke.MethodHandles;

public class InnerClassReferencer {
	public InnerClassExample.Inner test1() {
		throw new UnsupportedOperationException();
	}

	public org.minimallycorrect.javatransformer.transform.InnerClassExample.Inner2 test2() {
		throw new UnsupportedOperationException();
	}

	public AnnotationInnerClassExample.TestEnum test3() {
		throw new UnsupportedOperationException();
	}

	public org.minimallycorrect.javatransformer.transform.AnnotationInnerClassExample.TestEnum test4() {
		throw new UnsupportedOperationException();
	}

	public MethodHandles.Lookup test5() {
		throw new UnsupportedOperationException();
	}

	public java.lang.invoke.MethodHandles.Lookup test6() {
		throw new UnsupportedOperationException();
	}
}
