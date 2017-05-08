package me.nallar.javatransformer.internal.util;

import lombok.val;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.util.*;

public class FilteringClassWriter extends ClassWriter {
	public final Map<String, String> filters = new HashMap<String, String>();

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
		val replace = filters.get(value);
		if (replace != null)
			value = replace;

		return super.newClass(value);
	}

	@Override
	public int newConst(Object value) {
		if (value instanceof String) {
			val replace = filters.get(value);
			if (replace != null)
				value = replace;
		}

		return super.newConst(value);
	}

	@Override
	public int newUTF8(String value) {
		val replace = filters.get(value);
		if (replace != null)
			throw new IllegalStateException("Should have already replaced " + value + " earlier with " + replace);

		return super.newUTF8(value);
	}
}
