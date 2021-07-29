package dev.minco.javatransformer.internal.util;

import java.lang.invoke.MethodHandles;

import lombok.SneakyThrows;
import lombok.val;

public class LookupAccess {
	private static final MethodHandles.Lookup IMPL_LOOKUP;

	@SneakyThrows
	public static MethodHandles.Lookup privateLookupFor(Class<?> clazz) {
		if (IMPL_LOOKUP != null) {
			return IMPL_LOOKUP;
		}

		MethodHandles.Lookup lookup = MethodHandles.lookup();

		Object privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class)
			.invoke(null, clazz, lookup);
		return (MethodHandles.Lookup) privateLookupIn;
	}

	static {
		MethodHandles.Lookup lookup = null;
		try {
			val field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			field.setAccessible(true);
			lookup = (MethodHandles.Lookup) field.get(null);
		} catch (Throwable t) {
			System.err.println("Failed to get MethodHandles.Lookup.IMPL_LOOKUP");
			t.printStackTrace();
		}
		IMPL_LOOKUP = lookup;
	}
}
