package org.minimallycorrect.javatransformer.api.code;

@SuppressWarnings("unused")
public class RETURN {
	public static ShouldNotBeCalledError VOID() {
		throw new ShouldNotBeCalledError();
	}

	public static <T> ShouldNotBeCalledError OBJECT(T value) {
		throw new ShouldNotBeCalledError();
	}

	public static ShouldNotBeCalledError BYTE(byte value) {
		throw new ShouldNotBeCalledError();
	}

	public static ShouldNotBeCalledError SHORT(short value) {
		throw new ShouldNotBeCalledError();
	}

	public static ShouldNotBeCalledError INT(int value) {
		throw new ShouldNotBeCalledError();
	}

	public static ShouldNotBeCalledError LONG(long value) {
		throw new ShouldNotBeCalledError();
	}

	public static ShouldNotBeCalledError BOOLEAN(boolean value) {
		throw new ShouldNotBeCalledError();
	}

	public static ShouldNotBeCalledError FLOAT(float value) {
		throw new ShouldNotBeCalledError();
	}

	public static ShouldNotBeCalledError DOUBLE(double value) {
		throw new ShouldNotBeCalledError();
	}
}
