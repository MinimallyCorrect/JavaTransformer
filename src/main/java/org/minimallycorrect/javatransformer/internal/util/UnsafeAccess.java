package org.minimallycorrect.javatransformer.internal.util;

import java.lang.invoke.MethodHandles;

import lombok.val;

import com.github.javaparser.utils.Log;

public class UnsafeAccess {
	public static final MethodHandles.Lookup IMPL_LOOKUP;

	static {
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		try {
			val field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			field.setAccessible(true);
			lookup = (MethodHandles.Lookup) field.get(null);
		} catch (Throwable t) {
			Log.error("Failed to get MethodHandles.Lookup.IMPL_LOOKUP", t);
		}
		IMPL_LOOKUP = lookup;
	}
}
