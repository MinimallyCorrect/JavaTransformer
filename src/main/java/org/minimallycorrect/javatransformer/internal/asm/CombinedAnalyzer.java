package org.minimallycorrect.javatransformer.internal.asm;

import java.util.ArrayList;
import java.util.List;

import lombok.val;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

public class CombinedAnalyzer<V extends Value> implements Opcodes {
	private final Interpreter<V> interpreter;
	private Frame<V>[] frames;
	private boolean[] queued;
	private int[] queue;
	private int top;

	private CombinedAnalyzer(Interpreter<V> interpreter) {
		this.interpreter = interpreter;
	}

	@SuppressWarnings("unchecked")
	public static <A extends Value> Frame<A>[] analyze(Interpreter<A> interpreter, final String owner, final MethodNode m) throws AnalyzerException {
		if ((m.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0 || m.instructions.size() == 0) {
			return (Frame<A>[]) new Frame<?>[0];
		}
		return new CombinedAnalyzer<>(interpreter).analyze(owner, m);
	}

	@SuppressWarnings("unchecked")
	private Frame<V>[] analyze(final String owner, final MethodNode m) throws AnalyzerException {
		int n = m.instructions.size();
		InsnList insns = m.instructions;
		List<TryCatchBlockNode>[] handlers = (List<TryCatchBlockNode>[]) new List<?>[n];
		frames = (Frame<V>[]) new Frame<?>[n + 1];
		queued = new boolean[n];
		queue = new int[n];
		top = 0;

		// computes exception handlers for each instruction
		for (int i = 0; i < m.tryCatchBlocks.size(); ++i) {
			TryCatchBlockNode tcb = m.tryCatchBlocks.get(i);
			int begin = insns.indexOf(tcb.start);
			int end = insns.indexOf(tcb.end);
			for (int j = begin; j < end; ++j) {
				List<TryCatchBlockNode> insnHandlers = handlers[j];
				if (insnHandlers == null) {
					insnHandlers = new ArrayList<>();
					handlers[j] = insnHandlers;
				}
				insnHandlers.add(tcb);
			}
		}

		// initializes the data structures for the control flow analysis
		Frame<V> current = new Frame<>(m.maxLocals, m.maxStack);
		Frame<V> handler = new Frame<>(m.maxLocals, m.maxStack);
		current.setReturn(interpreter.newValue(Type.getReturnType(m.desc)));
		Type[] args = Type.getArgumentTypes(m.desc);
		int local = 0;
		if ((m.access & ACC_STATIC) == 0) {
			Type ctype = Type.getObjectType(owner);
			current.setLocal(local++, interpreter.newValue(ctype));
		}
		for (Type arg : args) {
			current.setLocal(local++, interpreter.newValue(arg));
			if (arg.getSize() == 2) {
				current.setLocal(local++, interpreter.newValue(null));
			}
		}
		while (local < m.maxLocals) {
			current.setLocal(local++, interpreter.newValue(null));
		}
		merge(0, current);

		// control flow analysis
		while (top > 0) {
			int insn = queue[--top];
			Frame<V> f = frames[insn];
			queued[insn] = false;

			AbstractInsnNode insnNode = null;
			try {
				insnNode = m.instructions.get(insn);
				int insnOpcode = insnNode.getOpcode();
				int insnType = insnNode.getType();

				if (insnType == AbstractInsnNode.LABEL || insnType == AbstractInsnNode.LINE || insnType == AbstractInsnNode.FRAME) {
					merge(insn + 1, f);
				} else {
					current.init(f).execute(insnNode, interpreter);

					if (insnNode instanceof JumpInsnNode) {
						JumpInsnNode j = (JumpInsnNode) insnNode;
						if (insnOpcode != GOTO && insnOpcode != JSR) {
							merge(insn + 1, current);
						}
						int jump = insns.indexOf(j.label);
						merge(jump, current);
					} else if (insnNode instanceof LookupSwitchInsnNode) {
						LookupSwitchInsnNode lsi = (LookupSwitchInsnNode) insnNode;
						int jump = insns.indexOf(lsi.dflt);
						merge(jump, current);
						for (LabelNode label : lsi.labels) {
							jump = insns.indexOf(label);
							merge(jump, current);
						}
					} else if (insnNode instanceof TableSwitchInsnNode) {
						TableSwitchInsnNode tsi = (TableSwitchInsnNode) insnNode;
						int jump = insns.indexOf(tsi.dflt);
						merge(jump, current);
						for (LabelNode label : tsi.labels) {
							jump = insns.indexOf(label);
							merge(jump, current);
						}
					} else if (insnOpcode != ATHROW && (insnOpcode < IRETURN || insnOpcode > RETURN)) {
						merge(insn + 1, current);
					}
				}

				List<TryCatchBlockNode> insnHandlers = handlers[insn];
				if (insnHandlers != null) {
					for (TryCatchBlockNode tcb : insnHandlers) {
						Type type;
						if (tcb.type == null) {
							type = Type.getObjectType("java/lang/Throwable");
						} else {
							type = Type.getObjectType(tcb.type);
						}
						int jump = insns.indexOf(tcb.handler);
						handler.init(f);
						handler.clearStack();
						handler.push(interpreter.newValue(type));
						merge(jump, handler);
					}
				}
			} catch (Exception e) {
				val errorNode = e instanceof AnalyzerException ? ((AnalyzerException) e).node : insnNode;
				val opCode = insnNode == null ? -1 : insnNode.getOpcode();
				val message = "Error at instruction " + insn + " " + opCode + " " + e.getClass().getName() + ": " + e.getMessage();
				throw new AnalyzerException(errorNode, message, e);
			}
		}

		return frames;
	}

	private void merge(final int insn, final Frame<V> frame) throws AnalyzerException {
		Frame<V> oldFrame = frames[insn];
		boolean changes;

		if (oldFrame == null) {
			frames[insn] = new Frame<>(frame);
			changes = true;
		} else {
			changes = oldFrame.merge(frame, interpreter);
		}
		if (changes && insn < queued.length && !queued[insn]) {
			queued[insn] = true;
			queue[top++] = insn;
		}
	}

	public static class Frame<V extends Value> extends org.objectweb.asm.tree.analysis.Frame<V> {
		public Frame(int nLocals, int nStack) {
			super(nLocals, nStack);
		}

		public Frame(org.objectweb.asm.tree.analysis.Frame<? extends V> src) {
			super(src);
		}

		@Override
		@SuppressWarnings("unchecked")
		public V pop() {
			val top = getStackSize();
			if (top == 0) {
				push((V) CombinedValue.of(null, CombinedValue.POPPED_FROM_BOTTOM));
			}

			return super.pop();
		}
	}
}
