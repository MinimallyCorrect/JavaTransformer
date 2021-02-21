package dev.minco.javatransformer.transform.innerpackage;

import dev.minco.javatransformer.transform.InnerClassExample;

public class InnerClassReferencer {
	public InnerClassExample.Inner test1() {
		throw new UnsupportedOperationException();
	}

	public InnerClassExample.Inner2 test2() {
		throw new UnsupportedOperationException();
	}
}
