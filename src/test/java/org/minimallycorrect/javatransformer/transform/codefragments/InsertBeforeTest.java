package org.minimallycorrect.javatransformer.transform.codefragments;

@SuppressWarnings("ALL")
public class InsertBeforeTest {
	public String toInsert() {
		return "success";
	}

	public String testMethod() {
		throw new UnsupportedOperationException("test");
	}
}
