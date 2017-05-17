package org.minimallycorrect.javatransformer.api;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Parameter extends Type {
	public final String name;

	public Parameter(Type t, String name) {
		super(t.descriptor, t.signature);
		this.name = name;
	}

	public Parameter(String real, String generic, String name) {
		super(real, generic);
		this.name = name;
	}
}
