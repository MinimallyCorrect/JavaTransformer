package org.minimallycorrect.javatransformer.transform;

import org.minimallycorrect.javatransformer.api.code.RETURN;

import java.io.*;
import java.util.function.*;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class CodeFragmentTesting {
	private Consumer<String> callback;

	public CodeFragmentTesting(Consumer<String> callback) {
		this.callback = callback;
	}

	public void testMethodCallExpression() {
		System.out.println("1");
		System.out.println("2");
		System.out.println("3");
		System.out.println("4");
	}

	private void callbackCaller(PrintStream out, String parameter) {
		callback.accept(parameter);
	}

	private void methodToInsert() {
		throw RETURN.VOID();
	}
}
