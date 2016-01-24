package me.nallar.javatransformer.internal;

import lombok.Data;
import me.nallar.javatransformer.api.*;

import java.util.*;

@Data
public class MethodInfoImplementation implements MethodInfo {
	public AccessFlags accessFlags;
	public String name;
	public Type returnType;
	public List<Parameter> parameters;
	public List<Annotation> annotations;

	private MethodInfoImplementation(AccessFlags accessFlags, String name, Type returnType, List<Parameter> parameters) {
		this.accessFlags = accessFlags;
		this.name = name;
		this.returnType = returnType;
		this.parameters = new ArrayList<>(parameters);
	}

	public static MethodInfo of(AccessFlags accessFlags, String name, Type returnType, List<Parameter> parameters) {
		return new MethodInfoImplementation(accessFlags, name, returnType, parameters);
	}

	public List<Parameter> getParameters() {
		return Collections.unmodifiableList(parameters);
	}

	@Override
	public ClassInfo getClassInfo() {
		return null;
	}
}
