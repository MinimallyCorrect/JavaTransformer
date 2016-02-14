package me.nallar.javatransformer.internal;

import lombok.Data;
import me.nallar.javatransformer.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class SimpleMethodInfo implements MethodInfo {
	public AccessFlags accessFlags;
	public String name;
	public Type returnType;
	public List<Parameter> parameters;
	public List<Annotation> annotations;

	private SimpleMethodInfo(AccessFlags accessFlags, String name, Type returnType, List<Parameter> parameters) {
		this.accessFlags = accessFlags;
		this.name = name;
		this.returnType = returnType;
		this.parameters = new ArrayList<>(parameters);
	}

	public static MethodInfo of(AccessFlags accessFlags, String name, Type returnType, List<Parameter> parameters) {
		return new SimpleMethodInfo(accessFlags, name, returnType, parameters);
	}

	public static String toString(MethodInfo info) {
		return info.getAccessFlags().toString() + ' ' + info.getReturnType() + ' ' + info.getName() + '(' + info.getParameters() + ')';
	}

	public List<Parameter> getParameters() {
		return Collections.unmodifiableList(parameters);
	}

	@Override
	public ClassInfo getClassInfo() {
		return null;
	}

	@Override
	public String toString() {
		return toString(this);
	}
}
