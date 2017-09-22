package org.minimallycorrect.javatransformer.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Data;

import org.jetbrains.annotations.Nullable;

import org.minimallycorrect.javatransformer.api.AccessFlags;
import org.minimallycorrect.javatransformer.api.Annotation;
import org.minimallycorrect.javatransformer.api.ClassInfo;
import org.minimallycorrect.javatransformer.api.MethodInfo;
import org.minimallycorrect.javatransformer.api.Parameter;
import org.minimallycorrect.javatransformer.api.Type;
import org.minimallycorrect.javatransformer.api.TypeVariable;

@Data
public class SimpleMethodInfo implements MethodInfo {
	public AccessFlags accessFlags;
	public List<TypeVariable> typeVariables;
	public Type returnType;
	public String name;
	public List<Parameter> parameters;
	public List<Annotation> annotations;

	private SimpleMethodInfo(AccessFlags accessFlags, List<TypeVariable> typeVariables, Type returnType, String name, List<Parameter> parameters) {
		this.accessFlags = accessFlags;
		this.typeVariables = new ArrayList<>(typeVariables);
		this.returnType = returnType;
		this.name = name;
		this.parameters = new ArrayList<>(parameters);
	}

	public static MethodInfo of(AccessFlags accessFlags, List<TypeVariable> typeVariables, Type returnType, String name, List<Parameter> parameters) {
		return new SimpleMethodInfo(accessFlags, typeVariables, returnType, name, parameters);
	}

	public static String toString(MethodInfo info) {
		return info.getAccessFlags().toString() + ' ' + info.getReturnType() + ' ' + info.getName() + '(' + info.getParameters() + ')';
	}

	public List<Parameter> getParameters() {
		return Collections.unmodifiableList(parameters);
	}

	@Nullable
	@Override
	public ClassInfo getClassInfo() {
		return null;
	}

	@Override
	public String toString() {
		return toString(this);
	}

	@Override
	@SuppressWarnings("MethodDoesntCallSuperMethod")
	public MethodInfo clone() {
		return of(accessFlags, typeVariables, returnType, name, parameters);
	}
}
