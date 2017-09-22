package org.minimallycorrect.javatransformer.internal;

import static org.minimallycorrect.javatransformer.api.code.IntermediateValue.LocationType.LOCAL;
import static org.minimallycorrect.javatransformer.api.code.IntermediateValue.LocationType.STACK;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Frame;

import org.minimallycorrect.javatransformer.api.AccessFlags;
import org.minimallycorrect.javatransformer.api.Parameter;
import org.minimallycorrect.javatransformer.api.Type;
import org.minimallycorrect.javatransformer.api.code.CodeFragment;
import org.minimallycorrect.javatransformer.api.code.IntermediateValue;
import org.minimallycorrect.javatransformer.internal.asm.AsmInstructions;
import org.minimallycorrect.javatransformer.internal.asm.CombinedValue;
import org.minimallycorrect.javatransformer.internal.asm.DebugPrinter;
import org.minimallycorrect.javatransformer.internal.util.Cloner;
import org.minimallycorrect.javatransformer.internal.util.CollectionUtil;
import org.minimallycorrect.javatransformer.internal.util.JVMUtil;

class AsmCodeFragmentGenerator implements Opcodes {
	static Class<?> concreteImplementation(Class<?> interfaceType) {
		if (interfaceType == CodeFragment.class)
			return MethodNodeInfoCodeFragment.class;
		if (interfaceType == CodeFragment.MethodCall.class)
			return MethodCall.class;
		if (interfaceType == CodeFragment.FieldAccess.class)
			return CodeFragment.FieldAccess.class;
		throw new UnsupportedOperationException("No ASM implementation for " + interfaceType);
	}

	private static boolean ivEqualIgnoringStackOffset(IntermediateValue t, IntermediateValue t1) {
		return t.type.equals(t1.type) && t.location.type.equals(t1.location.type);
	}

	@EqualsAndHashCode
	@RequiredArgsConstructor
	abstract static class AsmCodeFragment implements CodeFragment {
		@NonNull
		public final ByteCodeInfo.MethodNodeInfo containingMethodNodeInfo;

		@NonNull
		public abstract AbstractInsnNode getFirstInstruction();

		@NonNull
		public abstract AbstractInsnNode getLastInstruction();

		@Override
		public ExecutionOutcome getExecutionOutcome() {
			AbstractInsnNode current = getFirstInstruction();

			val last = getLastInstruction();

			val frames = containingMethodNodeInfo.getStackFrames();
			boolean canThrow = false;
			boolean canReturn = false;
			boolean canFallThrough;

			val list = containingMethodNodeInfo.node.instructions;
			while (true) {
				boolean canFallThroughThisInstruction = false;
				// must be reachable
				if (frames[list.indexOf(current)] != null)
					switch (current.getOpcode()) {
						// return instructions
						case Opcodes.ARETURN:
						case Opcodes.DRETURN:
						case Opcodes.FRETURN:
						case Opcodes.IRETURN:
						case Opcodes.LRETURN:
						case Opcodes.RETURN:
							canReturn = true;
							break;
						// through instructions
						case Opcodes.ATHROW:
							canThrow = true;
							break;
						// unconditional jump (never falls through)
						case Opcodes.GOTO:
							break;
						default:
							canFallThroughThisInstruction = true;
					}
				if (current == last) {
					canFallThrough = canFallThroughThisInstruction;
					break;
				}
				current = current.getNext();
			}

			if (!canFallThrough && !canThrow && !canReturn)
				throw new IllegalStateException("canFallThrough canThrow and canReturn all false for " + this + ", impossible execution outcome");

			return new ExecutionOutcome(canFallThrough, canThrow, canReturn);
		}

		@NonNull
		@Override
		public List<IntermediateValue> getInputTypes() {
			return getTypes(true, true, true);
		}

		@NonNull
		@Override
		public List<IntermediateValue> getOutputTypes() {
			return getTypes(false, true, true);
		}

		List<IntermediateValue> getTypes(boolean inputs, boolean stack, boolean locals) {
			val first = getFirstInstruction();
			val last = getLastInstruction();
			val node = containingMethodNodeInfo.node;
			val insnList = node.instructions;
			val frames = containingMethodNodeInfo.getStackFrames();
			val startIndex = insnList.indexOf(first);
			val endIndex = insnList.indexOf(last) + 1;

			// TODO: local outputs?
			val results = new ArrayList<IntermediateValue>();
			if (locals && inputs) {
				val usedLocals = new BitSet(node.maxLocals);
				AbstractInsnNode insn = first;
				while (true) {
					val opcode = insn.getOpcode();
					if ((opcode >= ILOAD && opcode < IALOAD) || insn instanceof VarInsnNode) {
						val varInsnNode = (VarInsnNode) insn;
						val target = varInsnNode.var;
						val frame = frames[insnList.indexOf(insn)];
						val local = frame.getLocal(target);
						if (local.isPrefilled() || !local.isInitialised())
							usedLocals.set(target);
					}
					if (insn == last)
						break;
					insn = insn.getNext();
				}
				setUsedLocalIndexes(usedLocals);

				for (int i = 0; i < usedLocals.size(); i++)
					if (usedLocals.get(i)) {
						org.objectweb.asm.Type type = null;
						for (int j = startIndex; j <= endIndex; j++) {
							val frame = frames[j];
							val local = frame.getLocal(i);
							if (local.isInitialised()) {
								type = local.getType();
								break;
							}
						}
						if (type == null)
							type = CombinedValue.OBJECT_TYPE;

						results.add(new IntermediateValue(new Type(type.getDescriptor()), IntermediateValue.UNKNOWN, new IntermediateValue.Location(LOCAL, i, null)));
					}

				// node.visibleLocalVariableAnnotations;
			}

			if (stack) {
				Frame<CombinedValue> firstFrame = frames[startIndex];
				Frame<CombinedValue> lastFrame = endIndex >= frames.length ? null : frames[endIndex];
				if (!inputs) {
					Frame<CombinedValue> temp = firstFrame;
					firstFrame = lastFrame;
					lastFrame = temp;
				}
				val fs = firstFrame == null ? 0 : firstFrame.getStackSize();
				val ls = lastFrame == null ? 0 : lastFrame.getStackSize();

				if (firstFrame == null && lastFrame == null) {
					DebugPrinter.printByteCode(containingMethodNodeInfo.node, "unexpected_null_frame");
					throw new IllegalStateException("frames were unreachable " + Arrays.toString(frames));
				}

				// Stack types
				for (int i = 0; i < fs; i++) {
					@SuppressWarnings("ConstantConditions")
					val origStackValue = i < ls ? lastFrame.getStack(i) : null;
					val stackValue = firstFrame.getStack(i);

					// FIXME: merged values which may sometimes be uninitialised are treated as initialised here?
					// can this ever happen for stack values?
					if (Objects.equals(stackValue, origStackValue))
						continue;

					assert stackValue.getType() != null;
					results.add(new IntermediateValue(new Type(stackValue.getType().getDescriptor()), stackValue.getConstantValue(), new IntermediateValue.Location(STACK, i, null)));
				}
			}

			return results;
		}

		protected void setUsedLocalIndexes(BitSet usedLocals) {}

		@Override
		public void insert(@NonNull CodeFragment fragmentOfAnyType, @NonNull InsertionPosition position, @NonNull InsertionOptions insertionOptions) {
			if (this.equals(fragmentOfAnyType)) {
				if (position == InsertionPosition.OVERWRITE)
					return;
				throw new UnsupportedOperationException("Can't insert a CodeFragment into itself");
			}
			if (!(fragmentOfAnyType instanceof AsmCodeFragment))
				throw new TypeMismatchException(AsmCodeFragment.class, fragmentOfAnyType);

			AsmCodeFragment fragment = (AsmCodeFragment) fragmentOfAnyType;

			val containingMethodNodeInfo = this.containingMethodNodeInfo;
			val containingMethodNode = containingMethodNodeInfo.node;
			val containingList = containingMethodNode.instructions;

			val first = getFirstInstruction();
			val last = getLastInstruction();

			val executionResult = getExecutionOutcome();
			if (!executionResult.canFallThrough && position == InsertionPosition.AFTER)
				throw new UnreachableInsertionException(this, InsertionPosition.AFTER);

			InsnList insertInstructions;
			{
				insertInstructions = Cloner.clone(fragment.containingMethodNodeInfo.node.instructions, fragment.getFirstInstruction(), fragment.getLastInstruction());
				val clonedMethod = Cloner.deepClone(fragment.containingMethodNodeInfo.node);
				clonedMethod.instructions = insertInstructions;
				clonedMethod.name += "_mod";
				// clonedMethod.maxLocals += 20;
				// clonedMethod.maxStack += 20;
				fragment = new MethodNodeInfoCodeFragment(containingMethodNodeInfo.getClassInfo().wrap(clonedMethod));
				DebugPrinter.printByteCode(clonedMethod, "base");
				applyInsertionOptions((MethodNodeInfoCodeFragment) fragment, insertionOptions);
				DebugPrinter.printByteCode(clonedMethod, "insertionOptions");
				convertTypes((MethodNodeInfoCodeFragment) fragment, position);
				DebugPrinter.printByteCode(clonedMethod, "convertedTypes");
			}

			val startIndex = containingList.indexOf(first);
			val endIndex = containingList.indexOf(last);
			if (startIndex == -1 || endIndex == -1)
				throw new ArrayIndexOutOfBoundsException();

			switch (position) {
				case BEFORE:
					containingList.insertBefore(first, insertInstructions);
					break;
				case OVERWRITE:
					containingList.insertBefore(first, insertInstructions);
					AbstractInsnNode current = first;
					while (true) {
						val next = current.getNext();
						containingList.remove(current);
						if (current == last)
							break;
						current = next;
					}
					break;
				case AFTER:
					val insertedExecutionResult = fragment.getExecutionOutcome();
					if (!executionResult.canFallThrough && insertedExecutionResult.canFallThrough)
						throw new UnreachableInsertionException(this, InsertionPosition.AFTER);
					containingList.insert(last, insertInstructions);
					break;
				default:
					throw new UnsupportedOperationException("TODO: not yet implemented for " + getClass() + ' ' + fragment.getClass() + ' ' + position + " from " + startIndex + " to " + endIndex);
			}
			containingMethodNodeInfo.markCodeDirty();
		}

		private void convertTypes(MethodNodeInfoCodeFragment insertFragment, InsertionPosition position) {
			List<IntermediateValue> existingInputTypes;
			List<IntermediateValue> existingOutputTypes;
			switch (position) {
				case BEFORE:
					existingOutputTypes = existingInputTypes = getInputTypes();
					break;
				case OVERWRITE:
					existingInputTypes = getInputTypes();
					existingOutputTypes = getOutputTypes();
					break;
				case AFTER:
					existingOutputTypes = existingInputTypes = getOutputTypes();
					break;
				default:
					return;
			}

			val inputTypes = insertFragment.getInputTypes();
			val outputTypes = insertFragment.getOutputTypes();

			if (CollectionUtil.equals(inputTypes, outputTypes, AsmCodeFragmentGenerator::ivEqualIgnoringStackOffset))
				return;

			val node = insertFragment.containingMethodNodeInfo.node;
			val insns = node.instructions;
			val movedInputTypes = new ArrayList<IntermediateValue>(existingInputTypes);
			val varInsns = new InsnList();
			int localIndex = containingMethodNodeInfo.node.maxLocals;
			{
				int lastStackIndex = Integer.MIN_VALUE;
				ListIterator<IntermediateValue> $ivs = movedInputTypes.listIterator();
				while ($ivs.hasNext()) {
					val iv = $ivs.next();
					if (iv.location.type != STACK)
						continue;
					val stackIndex = iv.location.index;
					if (stackIndex <= lastStackIndex)
						throw new IllegalStateException("Unexpected stack index" + stackIndex + ", not > last seen index " + lastStackIndex);
					lastStackIndex = stackIndex;
					varInsns.insert(new VarInsnNode(AsmInstructions.getStoreInstructionForType(iv), localIndex));
					$ivs.set(iv.withLocation(new IntermediateValue.Location(LOCAL, localIndex, iv.location.name)));
					System.out.println("added local " + localIndex + " for " + iv);
					localIndex++;
				}
				containingMethodNodeInfo.node.maxLocals = localIndex;
			}

			// at this point, the existing input types are all local variables

			val locals = new HashMap<Integer, Integer>();
			int index = 0;
			for (val iv : inputTypes) {
				if (iv.location.type == LOCAL && iv.location.index == 0 && !containingMethodNodeInfo.getAccessFlags().has(AccessFlags.ACC_STATIC)) {
					locals.put(0, 0);
					continue;
				}
				val input = movedInputTypes.get(index++);
				if (!iv.type.isAssignableFrom(input.type))
					throw new UnsupportedOperationException();
				switch (iv.location.type) {
					case STACK:
						val var = new VarInsnNode(AsmInstructions.getLoadInstructionForType(iv), input.location.index);
						insns.insert(var);
						break;
					case LOCAL:
						if (input.location.type != LOCAL)
							throw new IllegalStateException();
						locals.put(iv.location.index, input.location.index);
						break;
					default:
						throw new UnsupportedOperationException(iv.toString());
				}
			}

			rebaseLocals(locals, insertFragment, containingMethodNodeInfo.node.maxLocals);
			insns.insert(varInsns);

			// insns.insert(lastVarAdded);
			if (existingOutputTypes == existingInputTypes && outputTypes.isEmpty())
				for (val iv : existingOutputTypes) {
					if (iv.location.type != STACK)
						continue;
					localIndex--;
					if (localIndex < 0)
						throw new IllegalStateException("localIndex < 0");
					insns.add(new VarInsnNode(AsmInstructions.getLoadInstructionForType(iv), localIndex));
				}

			insertFragment.containingMethodNodeInfo.markCodeDirty();
		}

		private void applyInsertionOptions(MethodNodeInfoCodeFragment fragment, InsertionOptions options) {
			val inputTypes = fragment.getInputTypes();
			System.out.println(String.join("\n", inputTypes.stream().map(IntermediateValue::toString).collect(Collectors.toList())));

			val containingMethodNodeInfo = fragment.containingMethodNodeInfo;
			val insns = containingMethodNodeInfo.node.instructions;
			if (options.convertReturnToOutputTypes) {
				LabelNode endLabel = null;
				val last = insns.getLast();
				Frame<CombinedValue>[] frames = null;
				// TODO: don't use toArray to iterate
				for (val current : insns.toArray()) {
					val opcode = current.getOpcode();
					if (opcode >= IRETURN && opcode <= RETURN) {
						// no need to jump if at the last instruction
						if (frames == null)
							frames = containingMethodNodeInfo.getStackFrames();
						val frame = frames[insns.indexOf(current)];
						if (frame.getStackSize() != (opcode == RETURN ? 0 : 1))
							throw new UnsupportedOperationException("TODO: handle non-blank stack at return instruction - allowed but not often done" + frame);

						if (current != last) {
							if (endLabel == null) {
								if (last instanceof LabelNode)
									endLabel = (LabelNode) last;
								else {
									endLabel = new LabelNode();
									insns.insert(last, endLabel);
								}
							}
							if (current.getNext() != endLabel)
								insns.insert(current, new JumpInsnNode(GOTO, endLabel));
						}
						insns.remove(current);
						containingMethodNodeInfo.markCodeDirty();
					}
				}
			}
			if (options.convertReturnCallToReturnInstruction) {
				@NonNull
				AbstractInsnNode current = fragment.getFirstInstruction();
				@NonNull
				AbstractInsnNode last = fragment.getLastInstruction();
				while (true) {
					int opcode = current.getOpcode();
					if (opcode == INVOKESTATIC || current instanceof MethodInsnNode) {
						val methodInsnNode = (MethodInsnNode) current;
						if (JVMUtil.classNameToSlashName(org.minimallycorrect.javatransformer.api.code.RETURN.class).equals(methodInsnNode.owner)) {
							val desc = new MethodDescriptor(methodInsnNode.desc, null);
							val params = desc.getParameters();
							Parameter first = params.isEmpty() ? null : params.get(0);
							insns.insertBefore(current, new InsnNode(AsmInstructions.getReturnInstructionForType(first == null ? null : first.type)));
							containingMethodNodeInfo.markCodeDirty();
							if (current == last) {
								insns.remove(current);
								break;
							}
							val next = current.getNext();
							insns.remove(current);
							current = next;
						}
					}
					if (current == last)
						break;
					current = current.getNext();
				}
			}
			if (options.eliminateDeadCode) {
				val frames = containingMethodNodeInfo.getStackFrames();
				for (int i = insns.size() - 1; i >= 0; i--) {
					if (frames[i] == null) {
						insns.remove(insns.get(i));
						containingMethodNodeInfo.markCodeDirty();
					}
				}
			}
		}

		private void rebaseLocals(HashMap<Integer, Integer> locals, MethodNodeInfoCodeFragment fragment, int offset) {
			if (offset == 0)
				return;
			val containingMethodNodeInfo = fragment.containingMethodNodeInfo;
			val node = containingMethodNodeInfo.node;
			node.maxLocals += offset;
			AbstractInsnNode current = node.instructions.getFirst();
			val last = node.instructions.getLast();
			while (true) {
				if (current instanceof VarInsnNode) {
					val index = ((VarInsnNode) current).var;
					val mapped = locals.get(index);
					((VarInsnNode) current).var = mapped == null ? index + offset : mapped;
				}
				if (current == last)
					return;
				current = current.getNext();
			}
		}

		@Override
		@SuppressWarnings({"JavaReflectionMemberAccess", "unchecked"})
		@SneakyThrows
		public <T extends CodeFragment> List<T> findFragments(Class<T> fragmentType) {
			if (fragmentType.isAssignableFrom(this.getClass()))
				return Collections.singletonList((T) this);

			val constructor = (Constructor<T>) concreteImplementation(fragmentType).getDeclaredConstructors()[0];

			val result = new ArrayList<T>();
			AbstractInsnNode insn = getFirstInstruction();
			val last = getLastInstruction();
			while (true) {
				if (constructor.getParameterTypes()[1].isAssignableFrom(insn.getClass()))
					result.add(constructor.newInstance(containingMethodNodeInfo, insn));
				if (insn == last)
					break;
				insn = insn.getNext();
			}

			return result;
		}
	}

	abstract static class InstructionCodeFragment extends AsmCodeFragment {
		InstructionCodeFragment(ByteCodeInfo.MethodNodeInfo containingMethodNodeInfo) {
			super(containingMethodNodeInfo);
		}

		public abstract AbstractInsnNode getInstruction();

		@NonNull
		@Override
		public final AbstractInsnNode getFirstInstruction() {
			return getInstruction();
		}

		@NonNull
		@Override
		public final AbstractInsnNode getLastInstruction() {
			return getInstruction();
		}
	}

	static class MethodNodeInfoCodeFragment extends AsmCodeFragment implements CodeFragment.Body {
		MethodNodeInfoCodeFragment(ByteCodeInfo.MethodNodeInfo containingMethodNodeInfo) {
			super(containingMethodNodeInfo);
		}

		@Override
		protected void setUsedLocalIndexes(BitSet set) {
			int i = 0;

			if (!containingMethodNodeInfo.getAccessFlags().has(AccessFlags.ACC_STATIC))
				set.set(i++);

			set.set(i, i + containingMethodNodeInfo.getParameters().size());

			if (i > containingMethodNodeInfo.node.maxLocals)
				throw new IllegalStateException();
		}

		@NonNull
		@Override
		public AbstractInsnNode getFirstInstruction() {
			return containingMethodNodeInfo.node.instructions.getFirst();
		}

		@NonNull
		@Override
		public AbstractInsnNode getLastInstruction() {
			return containingMethodNodeInfo.node.instructions.getLast();
		}
	}

	@Getter
	static class MethodCall extends InstructionCodeFragment implements CodeFragment.MethodCall {
		private final MethodInsnNode instruction;

		public MethodCall(ByteCodeInfo.MethodNodeInfo containingMethodNodeInfo, MethodInsnNode instruction) {
			super(containingMethodNodeInfo);
			this.instruction = instruction;
		}

		@NonNull
		@Override
		public Type getContainingClassType() {
			return new Type('L' + instruction.owner + ';');
		}

		@NonNull
		@Override
		public String getName() {
			return instruction.name;
		}
	}
}
