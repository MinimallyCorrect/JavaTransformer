package org.minimallycorrect.javatransformer.internal.util;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import lombok.val;

import sun.misc.Unsafe;

import com.github.javaparser.utils.Log;

public class UnsafeAccess {
	public static final Unsafe $;
	public static final MethodHandles.Lookup IMPL_LOOKUP;

	static {
		Unsafe temp = null;
		try {
			Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			temp = (Unsafe) theUnsafe.get(null);
		} catch (Throwable t) {
			Log.error("Failed to get unsafe", t);
		}
		$ = temp;

		MethodHandles.Lookup lookup = MethodHandles.lookup();
		try {
			val field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			if ($ != null) {
				lookup = (MethodHandles.Lookup) $.getObject($.staticFieldBase(field), $.staticFieldOffset(field));
			} else {
				field.setAccessible(true);
				lookup = (MethodHandles.Lookup) field.get(null);
			}
		} catch (Throwable t) {
			Log.error("Failed to get MethodHandles.Lookup.IMPL_LOOKUP", t);
		}
		IMPL_LOOKUP = lookup;
	}
}
