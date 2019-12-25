package org.minimallycorrect.javatransformer.internal.asm;

import java.util.HashMap;
import java.util.Map;

import lombok.val;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class FilteringClassWriter extends ClassWriter {
	public final Map<String, String> filters = new HashMap<>();

	public FilteringClassWriter(int flags) {
		super(flags);
	}

	public FilteringClassWriter(ClassReader classReader, int flags) {
		super(classReader, flags);
	}

	public static void addFilter(Map<String, String> filters, String a, String b) {
		filters.put(a, b);
		val a2 = a.replace('.', '/');
		val b2 = b.replace('.', '/');
		filters.put(a2, b2);
		filters.put('L' + a2 + ';', 'L' + b2 + ';');
	}

	@Override
	public int newClass(String value) {
		return super.newClass(replace(value));
	}

	@Override
	public int newConst(Object value) {
		if (value instanceof String) {
			value = replace((String) value);
		}

		return super.newConst(value);
	}

	@Override
	public int newNameType(final String name, final String desc) {
		return super.newNameType(name, replace(desc));
	}

	@Override
	public int newHandle(int tag, String owner, String name, String descriptor, boolean isInterface) {
		return super.newHandle(tag, replace(owner), name, descriptor, isInterface);
	}

	@Override
	@SuppressWarnings("deprecation")
	public int newHandle(int tag, String owner, String name, String desc) {
		return super.newHandle(tag, replace(owner), name, desc);
	}

	@Override
	public int newField(String owner, String name, String desc) {
		return super.newField(replace(owner), name, desc);
	}

	private String replace(String s) {
		val replaced = filters.get(s);
		return replaced == null ? s : replaced;
	}

	@Override
	public int newUTF8(String value) {
		return super.newUTF8(replace(value));
	}

	@Override
	protected String getCommonSuperClass(final String a, final String b) {
		if ((a.indexOf('.') != -1 && !a.startsWith("java.")) || (b.indexOf('.') != -1 && !b.startsWith("java.")))
			throw new UnsupportedOperationException();

		return super.getCommonSuperClass(a, b);
	}
}
