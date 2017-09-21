package org.minimallycorrect.javatransformer.internal.asm;

import lombok.EqualsAndHashCode;
import lombok.val;
import org.jetbrains.annotations.Nullable;
import org.minimallycorrect.javatransformer.api.code.IntermediateValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.analysis.Value;

import java.util.*;

@EqualsAndHashCode
public class CombinedValue implements Value, Opcodes {
	public static final Type OBJECT_TYPE = Type.getObjectType("java/lang/Object");
	public static final AbstractInsnNode POPPED_FROM_BOTTOM = new InsnNode(NOP) {
		@Override
		public String toString() {
			return "Popped from empty stack.";
		}
	};
	public static final AbstractInsnNode PREFILLED = new InsnNode(NOP) {
		@Override
		public String toString() {
			return "Pre-filled value. Method parameter, this, or caught exception/NOP";
		}
	};
	private static final CombinedValue UNINITIALIZED_VALUE = new CombinedValue(null, Collections.emptySet());
	private static final CombinedValue INT_VALUE = new CombinedValue(Type.INT_TYPE, Collections.emptySet());
	private static final CombinedValue FLOAT_VALUE = new CombinedValue(Type.FLOAT_TYPE, Collections.emptySet());
	private static final CombinedValue LONG_VALUE = new CombinedValue(Type.LONG_TYPE, Collections.emptySet());
	private static final CombinedValue DOUBLE_VALUE = new CombinedValue(Type.DOUBLE_TYPE, Collections.emptySet());
	private static final CombinedValue REFERENCE_VALUE = new CombinedValue(OBJECT_TYPE, Collections.emptySet());
	/**
	 * The instructions that can produce this value. For example, for the Java code below, the instructions that can produce the value of <tt>i</tt> at line 5 are the txo ISTORE instructions at line 1 and 3:
	 * 
	 * <pre>
	 * 1: i = 0;
	 * 2: if (...) {
	 * 3:   i = 1;
	 * 4: }
	 * 5: return i;
	 * </pre>
	 * 
	 * This field is a set of {@link AbstractInsnNode} objects.
	 */
	public final Set<AbstractInsnNode> insns;
	@Nullable
	private final Type type;

	protected CombinedValue(@Nullable final Type type, final Set<AbstractInsnNode> insns) {
		this.type = type;
		this.insns = insns;
	}

	@Nullable
	@Deprecated
	public static CombinedValue of(@Nullable Type type) {
		return of(type, Collections.emptySet());
	}

	@Nullable
	public static CombinedValue of(@Nullable Type type, AbstractInsnNode insn) {
		return of(type, Collections.singleton(insn));
	}

	@Nullable
	public static CombinedValue of(@Nullable Type type, Set<AbstractInsnNode> insns) {
		if (type != null && type.getSort() == Type.VOID && insns.size() == 1 && insns.contains(PREFILLED))
			return null;
		if (!insns.isEmpty())
			return new CombinedValue(type, insns);
		if (type == null)
			return CombinedValue.UNINITIALIZED_VALUE;
		switch (type.getSort()) {
			case Type.VOID:
				return null;
			case Type.BOOLEAN:
			case Type.CHAR:
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				return CombinedValue.INT_VALUE;
			case Type.FLOAT:
				return CombinedValue.FLOAT_VALUE;
			case Type.LONG:
				return CombinedValue.LONG_VALUE;
			case Type.DOUBLE:
				return CombinedValue.DOUBLE_VALUE;
			case Type.ARRAY:
			case Type.OBJECT:
				return type.getInternalName().equals("java/lang/Object") ? CombinedValue.REFERENCE_VALUE : new CombinedValue(type, insns);
			default:
				throw new IllegalArgumentException("Unhandled type" + type.getSort() + " " + type);
		}
	}

	@Nullable
	public Type getType() {
		return type;
	}

	public int getSize() {
		return type == Type.LONG_TYPE || type == Type.DOUBLE_TYPE ? 2 : 1;
	}

	public boolean isReference() {
		return type != null && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY);
	}

	public boolean isInitialised() {
		return type != null;
	}

	@Nullable
	public Object getConstantValue() {
		val iterator = insns.iterator();
		if (!iterator.hasNext())
			return IntermediateValue.UNKNOWN;
		Object value = AsmInstructions.getConstant(iterator.next());
		while (iterator.hasNext()) {
			Object newValue = AsmInstructions.getConstant(iterator.next());
			if (!Objects.equals(newValue, value))
				return IntermediateValue.UNKNOWN;
		}
		return value;
	}

	public String getDescriptor() {
		if (this == UNINITIALIZED_VALUE || type == null) {
			return ".";
		} else {
			return type.getDescriptor();
		}
	}

	@Override
	public String toString() {
		return "type: " + getDescriptor() + " " + insns;
	}

	public boolean isPrefilled() {
		return insns != null && insns.contains(CombinedValue.PREFILLED);
	}
}
