package me.nallar.javatransformer.api;

import me.nallar.javatransformer.internal.SimpleMethodInfo;

import java.util.*;

public interface MethodInfo extends ClassMember, HasTypeVariable {
	static MethodInfo of(AccessFlags accessFlags, List<TypeVariable> typeVariables, Type returnType, String name, Parameter... parameters) {
		return of(accessFlags, typeVariables, returnType, name, Arrays.asList(parameters));
	}

	static MethodInfo of(AccessFlags accessFlags, List<TypeVariable> typeVariables, Type returnType, String name, List<Parameter> parameters) {
		return SimpleMethodInfo.of(accessFlags, typeVariables, returnType, name, parameters);
	}

	Type getReturnType();

	void setReturnType(Type returnType);

	List<Parameter> getParameters();

	void setParameters(List<Parameter> parameters);

	default void setAll(MethodInfo info) {
		this.setName(info.getName());
		this.setAccessFlags(info.getAccessFlags());
		this.setReturnType(info.getReturnType());
		this.setParameters(info.getParameters());
		this.setTypeVariables(info.getTypeVariables());
	}

	default boolean similar(MethodInfo other) {
		return other.getName().equals(this.getName()) &&
			other.getReturnType().similar(this.getReturnType()) &&
			(other.getParameters() == null || this.getParameters() == null || other.getParameters().equals(this.getParameters()));
	}
}
