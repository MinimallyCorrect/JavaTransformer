package org.minimallycorrect.javatransformer.api;

import lombok.Data;

@Data
public class TypeVariable {
	private final String name;
	private final Type bounds;

	public TypeVariable(String name) {
		this(name, Type.OBJECT);
	}

	public TypeVariable(String name, Type bounds) {
		this.name = name;
		this.bounds = bounds;
	}

	@Override
	public String toString() {
		return name + ':' + bounds.signatureElseDescriptor();
	}
}
