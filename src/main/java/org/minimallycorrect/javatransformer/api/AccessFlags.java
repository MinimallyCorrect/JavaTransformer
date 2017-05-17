package org.minimallycorrect.javatransformer.api;

import com.github.javaparser.ast.Modifier;
import lombok.val;
import org.minimallycorrect.javatransformer.internal.util.JVMUtil;

import java.util.*;

public class AccessFlags {
	public static final int ACC_PUBLIC = 0x0001; // class, field, method
	public static final int ACC_PRIVATE = 0x0002; // class, field, method
	public static final int ACC_PROTECTED = 0x0004; // class, field, method
	public static final int ACC_STATIC = 0x0008; // field, method
	public static final int ACC_FINAL = 0x0010; // class, field, method, parameter
	public static final int ACC_SUPER = 0x0020; // class
	public static final int ACC_SYNCHRONIZED = 0x0020; // method
	public static final int ACC_VOLATILE = 0x0040; // field
	public static final int ACC_BRIDGE = 0x0040; // method
	public static final int ACC_VARARGS = 0x0080; // method
	public static final int ACC_TRANSIENT = 0x0080; // field
	public static final int ACC_NATIVE = 0x0100; // method
	public static final int ACC_INTERFACE = 0x0200; // class
	public static final int ACC_ABSTRACT = 0x0400; // class, method
	public static final int ACC_STRICT = 0x0800; // method
	public static final int ACC_SYNTHETIC = 0x1000; // class, field, method, parameter
	public static final int ACC_ANNOTATION = 0x2000; // class
	public static final int ACC_ENUM = 0x4000; // class(?) field inner
	public static final int ACC_MANDATED = 0x8000; // parameter
	public final int access;

	public AccessFlags(int access) {
		this.access = access;
	}

	public AccessFlags(EnumSet<Modifier> modifiers) {
		this(accessFor(modifiers));
	}

	private static int accessFor(EnumSet<Modifier> modifiers) {
		int access = 0;
		if (modifiers.contains(Modifier.PUBLIC))
			access |= ACC_PUBLIC;
		if (modifiers.contains(Modifier.PRIVATE))
			access |= ACC_PRIVATE;
		if (modifiers.contains(Modifier.PROTECTED))
			access |= ACC_PROTECTED;
		if (modifiers.contains(Modifier.STATIC))
			access |= ACC_STATIC;
		if (modifiers.contains(Modifier.FINAL))
			access |= ACC_FINAL;
		if (modifiers.contains(Modifier.SYNCHRONIZED))
			access |= ACC_SYNCHRONIZED;
		if (modifiers.contains(Modifier.VOLATILE))
			access |= ACC_VOLATILE;
		if (modifiers.contains(Modifier.TRANSIENT))
			access |= ACC_TRANSIENT;
		if (modifiers.contains(Modifier.NATIVE))
			access |= ACC_NATIVE;
		if (modifiers.contains(Modifier.ABSTRACT))
			access |= ACC_ABSTRACT;
		if (modifiers.contains(Modifier.STRICTFP))
			access |= ACC_STRICT;
		return access;
	}

	public EnumSet<Modifier> toJavaParserModifierSet() {
		val modifiers = new ArrayList<Modifier>();

		if (has(ACC_PUBLIC))
			modifiers.add(Modifier.PUBLIC);
		if (has(ACC_PRIVATE))
			modifiers.add(Modifier.PRIVATE);
		if (has(ACC_PROTECTED))
			modifiers.add(Modifier.PROTECTED);
		if (has(ACC_STATIC))
			modifiers.add(Modifier.STATIC);
		if (has(ACC_FINAL))
			modifiers.add(Modifier.FINAL);
		if (has(ACC_SYNCHRONIZED))
			modifiers.add(Modifier.SYNCHRONIZED);
		if (has(ACC_VOLATILE))
			modifiers.add(Modifier.VOLATILE);
		if (has(ACC_TRANSIENT))
			modifiers.add(Modifier.TRANSIENT);
		if (has(ACC_NATIVE))
			modifiers.add(Modifier.NATIVE);
		if (has(ACC_ABSTRACT))
			modifiers.add(Modifier.ABSTRACT);
		if (has(ACC_STRICT))
			modifiers.add(Modifier.STRICTFP);

		return EnumSet.copyOf(modifiers);
	}

	@Override
	public String toString() {
		return "Access: " + access + " (" + JVMUtil.accessIntToString(access) + ")";
	}

	@Override
	public boolean equals(Object o) {
		return o == this || (o instanceof AccessFlags && ((AccessFlags) o).access == access);
	}

	@Override
	public int hashCode() {
		return access;
	}

	public boolean has(int flag) {
		return (access & flag) == flag;
	}

	public AccessFlags makeAccessible(boolean needsPublic) {
		return new AccessFlags(JVMUtil.makeAccess(access, needsPublic));
	}

	public AccessFlags with(int flag) {
		return new AccessFlags(access | flag);
	}

	public AccessFlags without(int flag) {
		return new AccessFlags(access & ~flag);
	}
}
