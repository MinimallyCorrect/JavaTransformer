package org.minimallycorrect.javatransformer.internal.asm;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import lombok.val;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Interpreter;

public class CombinedInterpreter extends Interpreter<CombinedValue> implements Opcodes {
	public CombinedInterpreter() {
		super(ASM5);
	}

	protected CombinedInterpreter(final int api) {
		super(api);
	}

	@Nullable
	@Override
	public CombinedValue unaryOperation(final AbstractInsnNode insn,
		final CombinedValue value) throws AnalyzerException {
		switch (insn.getOpcode()) {
			case INEG:
			case IINC:
			case L2I:
			case F2I:
			case D2I:
			case I2B:
			case I2C:
			case I2S:
				return CombinedValue.of(Type.INT_TYPE, insn);
			case FNEG:
			case I2F:
			case L2F:
			case D2F:
				return CombinedValue.of(Type.FLOAT_TYPE, insn);
			case LNEG:
			case I2L:
			case F2L:
			case D2L:
				return CombinedValue.of(Type.LONG_TYPE, insn);
			case DNEG:
			case I2D:
			case L2D:
			case F2D:
				return CombinedValue.of(Type.DOUBLE_TYPE, insn);
			case IFEQ:
			case IFNE:
			case IFLT:
			case IFGE:
			case IFGT:
			case IFLE:
			case TABLESWITCH:
			case LOOKUPSWITCH:
			case IRETURN:
			case LRETURN:
			case FRETURN:
			case DRETURN:
			case ARETURN:
			case PUTSTATIC:
				return null;
			case GETFIELD:
				return CombinedValue.of(Type.getType(((FieldInsnNode) insn).desc), insn);
			case NEWARRAY:
				switch (((IntInsnNode) insn).operand) {
					case T_BOOLEAN:
						return CombinedValue.of(Type.getType("[Z"), insn);
					case T_CHAR:
						return CombinedValue.of(Type.getType("[C"), insn);
					case T_BYTE:
						return CombinedValue.of(Type.getType("[B"), insn);
					case T_SHORT:
						return CombinedValue.of(Type.getType("[S"), insn);
					case T_INT:
						return CombinedValue.of(Type.getType("[I"), insn);
					case T_FLOAT:
						return CombinedValue.of(Type.getType("[F"), insn);
					case T_DOUBLE:
						return CombinedValue.of(Type.getType("[D"), insn);
					case T_LONG:
						return CombinedValue.of(Type.getType("[J"), insn);
					default:
						throw new AnalyzerException(insn, "Invalid array type");
				}
			case ANEWARRAY:
				String desc = ((TypeInsnNode) insn).desc;
				return CombinedValue.of(Type.getType("[" + Type.getObjectType(desc)), insn);
			case ARRAYLENGTH:
				return CombinedValue.of(Type.INT_TYPE, insn);
			case ATHROW:
				return null;
			case CHECKCAST:
				desc = ((TypeInsnNode) insn).desc;
				return CombinedValue.of(Type.getObjectType(desc), Collections.singleton(insn));
			case INSTANCEOF:
				return CombinedValue.of(Type.INT_TYPE, insn);
			case MONITORENTER:
			case MONITOREXIT:
			case IFNULL:
			case IFNONNULL:
				return null;
			default:
				throw new Error("Internal error.");
		}
	}

	@Nullable
	@Override
	public CombinedValue ternaryOperation(final AbstractInsnNode insn, final CombinedValue value1, final CombinedValue value2, final CombinedValue value3) throws AnalyzerException {
		return null;
		//return naryOperation(insn, Arrays.asList(value1, value2, value3));
	}

	@Nullable
	@Override
	public CombinedValue naryOperation(final AbstractInsnNode insn, final List<? extends CombinedValue> values) throws AnalyzerException {
		int opcode = insn.getOpcode();
		if (opcode == MULTIANEWARRAY) {
			return CombinedValue.of(Type.getType(((MultiANewArrayInsnNode) insn).desc), insn);
		} else if (opcode == INVOKEDYNAMIC) {
			return CombinedValue.of(Type.getReturnType(((InvokeDynamicInsnNode) insn).desc), insn);
		} else {
			return CombinedValue.of(Type.getReturnType(((MethodInsnNode) insn).desc), insn);
		}
	}

	@Override
	public void returnOperation(final AbstractInsnNode insn, final CombinedValue value, final CombinedValue expected) {}

	@Nullable
	@Deprecated
	@Override
	public CombinedValue newValue(final Type type) {
		return CombinedValue.of(type, CombinedValue.PREFILLED);
	}

	@Nullable
	@Override
	public CombinedValue newOperation(final AbstractInsnNode insn) throws AnalyzerException {
		switch (insn.getOpcode()) {
			case ACONST_NULL:
				return CombinedValue.of(CombinedValue.OBJECT_TYPE, insn);
			case ICONST_M1:
			case ICONST_0:
			case ICONST_1:
			case ICONST_2:
			case ICONST_3:
			case ICONST_4:
			case ICONST_5:
			case BIPUSH:
			case SIPUSH:
				return CombinedValue.of(Type.INT_TYPE, insn);
			case LCONST_0:
			case LCONST_1:
				return CombinedValue.of(Type.LONG_TYPE, insn);
			case FCONST_0:
			case FCONST_1:
			case FCONST_2:
				return CombinedValue.of(Type.FLOAT_TYPE, insn);
			case DCONST_0:
			case DCONST_1:
				return CombinedValue.of(Type.DOUBLE_TYPE, insn);
			case LDC:
				Object cst = ((LdcInsnNode) insn).cst;
				if (cst instanceof Integer) {
					return CombinedValue.of(Type.INT_TYPE, insn);
				} else if (cst instanceof Float) {
					return CombinedValue.of(Type.FLOAT_TYPE, insn);
				} else if (cst instanceof Long) {
					return CombinedValue.of(Type.LONG_TYPE, insn);
				} else if (cst instanceof Double) {
					return CombinedValue.of(Type.DOUBLE_TYPE, insn);
				} else if (cst instanceof String) {
					return CombinedValue.of(Type.getObjectType("java/lang/String"), insn);
				} else if (cst instanceof Type) {
					int sort = ((Type) cst).getSort();
					if (sort == Type.OBJECT || sort == Type.ARRAY) {
						return CombinedValue.of(Type.getObjectType("java/lang/Class"), insn);
					} else if (sort == Type.METHOD) {
						return CombinedValue.of(Type.getObjectType("java/lang/invoke/MethodType"), insn);
					} else {
						throw new IllegalArgumentException("Illegal LDC constant " + cst + " with unknown sort " + sort);
					}
				} else if (cst instanceof Handle) {
					return CombinedValue.of(Type.getObjectType("java/lang/invoke/MethodHandle"), insn);
				} else {
					throw new IllegalArgumentException("Illegal LDC constant "
						+ cst);
				}
			case JSR:
				throw new UnsupportedOperationException("JSR not supported. Use JSRInlinerAdapter to inline JSR subroutines.");
			case GETSTATIC:
				return CombinedValue.of(Type.getType(((FieldInsnNode) insn).desc), insn);
			case NEW:
				return CombinedValue.of(Type.getObjectType(((TypeInsnNode) insn).desc), insn);
			default:
				throw new Error("Internal error.");
		}
	}

	@Override
	public CombinedValue copyOperation(final AbstractInsnNode insn, final CombinedValue value) throws AnalyzerException {
		// TODO: Is this right? SourceInterpreter does this, but isn't it more useful to keep the source as the one we copied from?
		// return CombinedValue.of(value.getType(), Collections.singleton(insn));

		return value;
	}

	@Nullable
	@Override
	public CombinedValue binaryOperation(final AbstractInsnNode insn, final CombinedValue value1, final CombinedValue value2)
		throws AnalyzerException {
		switch (insn.getOpcode()) {
			case IALOAD:
			case BALOAD:
			case CALOAD:
			case SALOAD:
			case IADD:
			case ISUB:
			case IMUL:
			case IDIV:
			case IREM:
			case ISHL:
			case ISHR:
			case IUSHR:
			case IAND:
			case IOR:
			case IXOR:
				return CombinedValue.of(Type.INT_TYPE, insn);
			case FALOAD:
			case FADD:
			case FSUB:
			case FMUL:
			case FDIV:
			case FREM:
				return CombinedValue.of(Type.FLOAT_TYPE, insn);
			case LALOAD:
			case LADD:
			case LSUB:
			case LMUL:
			case LDIV:
			case LREM:
			case LSHL:
			case LSHR:
			case LUSHR:
			case LAND:
			case LOR:
			case LXOR:
				return CombinedValue.of(Type.LONG_TYPE, insn);
			case DALOAD:
			case DADD:
			case DSUB:
			case DMUL:
			case DDIV:
			case DREM:
				return CombinedValue.of(Type.DOUBLE_TYPE, insn);
			case AALOAD:
				return CombinedValue.of(CombinedValue.OBJECT_TYPE, insn);
			case LCMP:
			case FCMPL:
			case FCMPG:
			case DCMPL:
			case DCMPG:
				return CombinedValue.of(Type.INT_TYPE, insn);
			case IF_ICMPEQ:
			case IF_ICMPNE:
			case IF_ICMPLT:
			case IF_ICMPGE:
			case IF_ICMPGT:
			case IF_ICMPLE:
			case IF_ACMPEQ:
			case IF_ACMPNE:
			case PUTFIELD:
				return null;
			default:
				throw new Error("Internal error.");
		}
	}

	@Nullable
	@Override
	public CombinedValue merge(final CombinedValue v, final CombinedValue w) {
		if (v.equals(w)) {
			return v;
		}
		Type type = v.getType();
		if (!Objects.equals(type, w.getType()))
			type = w.isReference() && v.isReference() ? CombinedValue.OBJECT_TYPE : null;

		val s = new HashSet<AbstractInsnNode>();
		s.addAll(v.insns);
		s.addAll(w.insns);
		return CombinedValue.of(type, s);
	}
}
