package me.nallar.javatransformer.api;

import me.nallar.javatransformer.internal.FieldInfoImplementation;

public interface FieldInfo extends ClassMember {
	static FieldInfo of(AccessFlags accessFlags, Type type, String name) {
		return FieldInfoImplementation.of(accessFlags, type, name);
	}

	Type getType();

	void setType(Type type);

	default void setAll(FieldInfo info) {
		this.setName(info.getName());
		this.setAccessFlags(info.getAccessFlags());
		this.setType(info.getType());
	}

	default boolean similar(FieldInfo other) {
		return other.getName().equals(this.getName()) &&
			other.getType().similar(this.getType());
	}
}
