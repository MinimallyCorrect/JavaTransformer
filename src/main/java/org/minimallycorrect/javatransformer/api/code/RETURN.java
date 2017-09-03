package org.minimallycorrect.javatransformer.api.code;

import org.jetbrains.annotations.Contract;

@SuppressWarnings("unused")
public class RETURN {
	@Contract(" -> fail")
	public static ShouldNotBeCalledError VOID() {
		throw new ShouldNotBeCalledError();
	}

	@Contract("_ -> fail")
	public static <T> ShouldNotBeCalledError OBJECT(T value) {
		throw new ShouldNotBeCalledError();
	}

	@Contract("_ -> fail")
	public static ShouldNotBeCalledError BYTE(byte value) {
		throw new ShouldNotBeCalledError();
	}

	@Contract("_ -> fail")
	public static ShouldNotBeCalledError SHORT(short value) {
		throw new ShouldNotBeCalledError();
	}

	@Contract("_ -> fail")
	public static ShouldNotBeCalledError INT(int value) {
		throw new ShouldNotBeCalledError();
	}

	@Contract("_ -> fail")
	public static ShouldNotBeCalledError LONG(long value) {
		throw new ShouldNotBeCalledError();
	}

	@Contract("_ -> fail")
	public static ShouldNotBeCalledError BOOLEAN(boolean value) {
		throw new ShouldNotBeCalledError();
	}

	@Contract("_ -> fail")
	public static ShouldNotBeCalledError FLOAT(float value) {
		throw new ShouldNotBeCalledError();
	}

	@Contract("_ -> fail")
	public static ShouldNotBeCalledError DOUBLE(double value) {
		throw new ShouldNotBeCalledError();
	}
}
