package org.minimallycorrect.javatransformer.transform;

import java.io.PrintStream;
import java.util.function.Consumer;

import org.minimallycorrect.javatransformer.api.code.RETURN;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class CodeFragmentTesting {
	private Consumer<String> callback;

	public CodeFragmentTesting(Consumer<String> callback) {
		this.callback = callback;
	}

	public void alternateMethodCallDemos() {
		System.getProperties();
		Runtime.getRuntime().totalMemory();
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

	public boolean testAbortEarly() {
		System.setProperty("finishedTestAbortEarly", "true");
		return false;
	}

	private void aborter() {
		throw RETURN.BOOLEAN(true);
	}

	private void methodToInsert() {
		throw RETURN.VOID();
	}
}
