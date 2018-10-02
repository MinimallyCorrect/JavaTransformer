package org.minimallycorrect.javatransformer.internal.javaparser;

import com.github.javaparser.ast.AccessSpecifier;

import org.minimallycorrect.javatransformer.api.AccessFlags;

public class AccessSpecifierUtil {
	public static AccessSpecifier fromAccessFlags(AccessFlags flags) {
		if (flags.has(AccessFlags.ACC_PUBLIC)) {
			return AccessSpecifier.PUBLIC;
		}
		if (flags.has(AccessFlags.ACC_PRIVATE)) {
			return AccessSpecifier.PRIVATE;
		}
		if (flags.has(AccessFlags.ACC_PROTECTED)) {
			return AccessSpecifier.PROTECTED;
		}
		return AccessSpecifier.DEFAULT;
	}
}
