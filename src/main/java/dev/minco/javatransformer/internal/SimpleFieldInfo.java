package dev.minco.javatransformer.internal;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

import dev.minco.javatransformer.api.AccessFlags;
import dev.minco.javatransformer.api.Annotation;
import dev.minco.javatransformer.api.ClassInfo;
import dev.minco.javatransformer.api.FieldInfo;
import dev.minco.javatransformer.api.Type;

@Data
@AllArgsConstructor
public class SimpleFieldInfo implements FieldInfo {
	public AccessFlags accessFlags;
	public String name;
	public Type type;
	public List<Annotation> annotations;
	public ClassInfo owner;

	public SimpleFieldInfo(AccessFlags accessFlags, Type type, String name) {
		this.accessFlags = accessFlags;
		this.type = type;
		this.name = name;
	}

	public static FieldInfo of(AccessFlags accessFlags, Type type, String name) {
		return new SimpleFieldInfo(accessFlags, type, name);
	}

	public static String toString(FieldInfo info) {
		return info.getAccessFlags().toString() + ' ' + info.getType() + ' ' + info.getName();
	}

	@Override
	public String toString() {
		return toString(this);
	}

	@Override
	public ClassInfo getClassInfo() {
		return null;
	}

	@Override
	@SuppressWarnings("MethodDoesntCallSuperMethod")
	public FieldInfo clone() {
		return of(accessFlags, type, name);
	}
}
