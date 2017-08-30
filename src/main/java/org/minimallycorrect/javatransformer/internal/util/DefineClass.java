package org.minimallycorrect.javatransformer.internal.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.invoke.*;
import java.security.*;

@UtilityClass
public class DefineClass {
	private static final sun.misc.Unsafe $ = UnsafeAccess.$;
	private static final MethodHandles.Lookup $L = UnsafeAccess.IMPL_LOOKUP;
	private static final ProtectionDomain PROTECTION_DOMAIN = AccessController.doPrivileged((PrivilegedAction<ProtectionDomain>) DefineClass.class::getProtectionDomain);
	private static final MethodHandle defineClassHandle = getDefineClassHandle();
	private static final boolean useUnsafe = $ != null && (defineClassHandle == null || System.getProperty("org.minimallycorrect.javatransformer.internal.util.DefineClass.useUnsafe", "false").equalsIgnoreCase("true"));

	private static MethodHandle getDefineClassHandle() {
		try {
			return $L.findSpecial(ClassLoader.class, "defineClass", MethodType.methodType(Class.class, String.class, byte[].class, Integer.TYPE, Integer.TYPE, ProtectionDomain.class), ClassLoader.class);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	@SneakyThrows
	@SuppressWarnings("unchecked")
	public static Class<?> defineClass(ClassLoader classLoader, String name, byte[] bytes) {
		if (useUnsafe) {
			return $.defineClass(name, bytes, 0, bytes.length, classLoader, PROTECTION_DOMAIN);
		}
		return (Class<?>) defineClassHandle.invokeExact(classLoader, name, bytes, 0, bytes.length, PROTECTION_DOMAIN);
	}
}
