package org.minimallycorrect.javatransformer.transform.innerpackage;

import org.minimallycorrect.javatransformer.transform.InnerClassExample;

public class InnerClassReferencer {
	public InnerClassExample.Inner test1() {
		throw new UnsupportedOperationException();
	}

	public org.minimallycorrect.javatransformer.transform.InnerClassExample.Inner2 test2() {
		throw new UnsupportedOperationException();
	}
}
