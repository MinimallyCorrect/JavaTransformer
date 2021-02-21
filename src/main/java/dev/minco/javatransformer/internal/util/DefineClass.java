package dev.minco.javatransformer.internal.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

import lombok.SneakyThrows;

public final class DefineClass {
	private static final MethodHandles.Lookup $L = UnsafeAccess.IMPL_LOOKUP;
	private static final ProtectionDomain PROTECTION_DOMAIN = AccessController.doPrivileged((PrivilegedAction<ProtectionDomain>) DefineClass.class::getProtectionDomain);
	private static final MethodHandle defineClassHandle = getDefineClassHandle();

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
		return (Class<?>) defineClassHandle.invokeExact(classLoader, name, bytes, 0, bytes.length, PROTECTION_DOMAIN);
	}
}
