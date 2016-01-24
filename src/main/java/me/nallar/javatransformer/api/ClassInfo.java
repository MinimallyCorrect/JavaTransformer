package me.nallar.javatransformer.api;

import java.util.*;
import java.util.function.*;

public interface ClassInfo extends ClassMember {
	default void add(ClassMember member) {
		if (member instanceof MethodInfo)
			add((MethodInfo) member);

		if (member instanceof FieldInfo)
			add((FieldInfo) member);

		throw new RuntimeException("Can't add member of type " + member.getClass().getCanonicalName() + " to " + this);
	}

	void add(MethodInfo method);

	void add(FieldInfo field);

	Type getSuperType();

	List<Type> getInterfaceTypes();

	List<MethodInfo> getMethods();

	List<FieldInfo> getFields();

	List<ClassMember> getMembers();

	default void accessFlags(Function<AccessFlags, AccessFlags> c) {
		setAccessFlags(c.apply(getAccessFlags()));
	}
}
