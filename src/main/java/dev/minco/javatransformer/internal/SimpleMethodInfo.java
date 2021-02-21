package dev.minco.javatransformer.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Data;

import org.jetbrains.annotations.Nullable;

import dev.minco.javatransformer.api.AccessFlags;
import dev.minco.javatransformer.api.Annotation;
import dev.minco.javatransformer.api.ClassInfo;
import dev.minco.javatransformer.api.MethodInfo;
import dev.minco.javatransformer.api.Parameter;
import dev.minco.javatransformer.api.Type;
import dev.minco.javatransformer.api.TypeVariable;

@Data
public class SimpleMethodInfo implements MethodInfo {
	public AccessFlags accessFlags;
	public List<TypeVariable> typeVariables;
	public Type returnType;
	public String name;
	public List<Parameter> parameters;
	public List<Annotation> annotations;
	@Nullable
	public ClassInfo classInfo;

	private SimpleMethodInfo(AccessFlags accessFlags, List<TypeVariable> typeVariables, Type returnType, String name, List<Parameter> parameters) {
		this.accessFlags = accessFlags;
		this.typeVariables = new ArrayList<>(typeVariables);
		this.returnType = returnType;
		this.name = name;
		this.parameters = new ArrayList<>(parameters);
	}

	public static SimpleMethodInfo of(AccessFlags accessFlags, List<TypeVariable> typeVariables, Type returnType, String name, List<Parameter> parameters) {
		return new SimpleMethodInfo(accessFlags, typeVariables, returnType, name, parameters);
	}

	public static String toString(MethodInfo info) {
		return info.getAccessFlags().toString() + ' ' + info.getReturnType() + ' ' + info.getName() + '(' + info.getParameters() + ')';
	}

	public List<Parameter> getParameters() {
		return Collections.unmodifiableList(parameters);
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
