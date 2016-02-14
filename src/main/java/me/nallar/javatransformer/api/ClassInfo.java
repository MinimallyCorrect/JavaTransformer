package me.nallar.javatransformer.api;

import java.util.*;
import java.util.function.*;

public interface ClassInfo extends ClassMember {
	default void add(ClassMember member) {
		if (member instanceof MethodInfo)
			add((MethodInfo) member);
		else if (member instanceof FieldInfo)
			add((FieldInfo) member);
		else
			throw new TransformationException("Can't add member of type " + member.getClass().getCanonicalName() + " to " + this);
	}

	void add(MethodInfo method);

	void add(FieldInfo field);

	default void remove(ClassMember member) {
		if (member instanceof MethodInfo)
			remove((MethodInfo) member);
		else if (member instanceof FieldInfo)
			remove((FieldInfo) member);
		else
			throw new TransformationException("Can't remove member of type " + member.getClass().getCanonicalName() + " to " + this);
	}

	void remove(MethodInfo method);

	void remove(FieldInfo field);

	Type getSuperType();

	List<Type> getInterfaceTypes();

	default ClassMember get(ClassMember member) {
		if (member instanceof MethodInfo)
			return get((MethodInfo) member);
		else if (member instanceof FieldInfo)
			return get((FieldInfo) member);
		else
			throw new TransformationException("Can't get member of type " + member.getClass().getCanonicalName() + " in " + this);
	}

	default MethodInfo get(MethodInfo like) {
		for (MethodInfo methodInfo : getMethods()) {
			if (like.similar(methodInfo))
				return methodInfo;
		}

		return null;
	}

	default FieldInfo get(FieldInfo like) {
		for (FieldInfo fieldInfo : getFields()) {
			if (like.similar(fieldInfo))
				return fieldInfo;
		}

		return null;
	}

	default Type getType() {
		return Type.of(getName());
	}

	List<MethodInfo> getMethods();

	List<FieldInfo> getFields();

	List<ClassMember> getMembers();

	default void accessFlags(Function<AccessFlags, AccessFlags> c) {
		setAccessFlags(c.apply(getAccessFlags()));
	}
}
