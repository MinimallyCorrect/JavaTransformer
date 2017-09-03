package org.minimallycorrect.javatransformer.api.code;

class ShouldNotBeCalledError extends Error {
	ShouldNotBeCalledError() {
		super("This method should never be called at runtime - it should be converted into a return instruction");
	}
}
