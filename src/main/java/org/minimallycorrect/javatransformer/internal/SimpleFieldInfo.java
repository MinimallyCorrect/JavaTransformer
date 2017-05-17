package org.minimallycorrect.javatransformer.internal;

import lombok.Data;
import org.minimallycorrect.javatransformer.api.*;

import java.util.*;

@Data
public class SimpleFieldInfo implements FieldInfo {
	public AccessFlags accessFlags;
	public String name;
	public Type type;
	public List<Annotation> annotations;

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
