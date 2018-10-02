package org.minimallycorrect.javatransformer.internal.asm;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.NonNull;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public class AsmUtil {
	@Nonnull
	public static ClassNode getClassNode(@NonNull byte[] data, @Nullable Holder<ClassReader> readerHolder) {
		ClassNode node = new ClassNode();
		ClassReader reader = new ClassReader(data);
		reader.accept(node, ClassReader.EXPAND_FRAMES);

		if (readerHolder != null)
			readerHolder.value = reader;

		return node;
	}

	public static class Holder<T> {
		public T value;
	}
}
