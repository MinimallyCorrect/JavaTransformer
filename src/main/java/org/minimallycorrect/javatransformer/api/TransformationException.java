package org.minimallycorrect.javatransformer.api;

/**
 * Thrown when parsing fails
 */
public class TransformationException extends RuntimeException {
	private static final long serialVersionUID = 0;

	public TransformationException() {
		super();
	}

	public TransformationException(String message) {
		super(message);
	}

	public TransformationException(String message, Throwable cause) {
		super(message, cause);
	}

	public TransformationException(Throwable cause) {
		super(cause);
	}

	protected TransformationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
