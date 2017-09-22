package org.minimallycorrect.javatransformer.api;

import java.util.function.Function;

public interface Accessible {
	AccessFlags getAccessFlags();

	void setAccessFlags(AccessFlags accessFlags);

	default void accessFlags(Function<AccessFlags, AccessFlags> c) {
		setAccessFlags(c.apply(getAccessFlags()));
	}
}
