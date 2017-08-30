package org.minimallycorrect.javatransformer.internal.util;

import lombok.experimental.UtilityClass;

import java.security.*;

@UtilityClass
public class DefineClass {
	private static final sun.misc.Unsafe $ = UnsafeAccess.$;
	private static final ProtectionDomain PROTECTION_DOMAIN = AccessController.doPrivileged((PrivilegedAction<ProtectionDomain>) DefineClass.class::getProtectionDomain);

	public static Class<?> defineClass(ClassLoader classLoader, String name, byte[] bytes) {
		return $.defineClass(name, bytes, 0, bytes.length, classLoader, PROTECTION_DOMAIN);
	}
}
