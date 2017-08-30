package org.minimallycorrect.javatransformer.internal.util;

import com.github.javaparser.utils.Log;
import lombok.val;
import sun.misc.Unsafe;

import java.lang.invoke.*;
import java.lang.reflect.*;

public class UnsafeAccess {
	public static final Unsafe $;
	public static final MethodHandles.Lookup IMPL_LOOKUP;

	static {
		Unsafe temp = null;
		try {
			Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			temp = (Unsafe) theUnsafe.get(null);
		} catch (Exception e) {
			Log.error("Failed to get unsafe", e);
		}
		$ = temp;

		MethodHandles.Lookup lookup = MethodHandles.lookup();
		try {
			val field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			lookup = (MethodHandles.Lookup) $.getObject($.staticFieldBase(field), $.staticFieldOffset(field));
		} catch (Exception e) {
			Log.error("Failed to get MethodHandles.Lookup.IMPL_LOOKUP");
		}
		IMPL_LOOKUP = lookup;
	}
}
