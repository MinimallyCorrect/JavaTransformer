package org.minimallycorrect.javatransformer.internal;

import lombok.*;
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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;

import static org.minimallycorrect.javatransformer.api.code.IntermediateValue.LocationType.LOCAL;
import static org.minimallycorrect.javatransformer.api.code.IntermediateValue.LocationType.STACK;

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

		@Override
		public List<IntermediateValue> getInputTypes() {
			return getTypes(true, true, true);
		}

		@Override
		public List<IntermediateValue> getOutputTypes() {
			return getTypes(false, true, true);
		}

		public List<IntermediateValue> getTypes(boolean inputs, boolean stack, boolean locals) {
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

				//node.visibleLocalVariableAnnotations;
			}

			if (stack) {
				val firstFrame = frames[inputs ? startIndex : endIndex];
				Frame<CombinedValue> lastFrame = endIndex >= frames.length ? null : frames[inputs ? endIndex : startIndex];
				val fs = firstFrame.getStackSize();
				val ls = lastFrame == null ? 0 : lastFrame.getStackSize();

				// Stack types
				for (int i = 0; i < fs; i++) {
					@SuppressWarnings("ConstantConditions")
					val origStackValue = i < ls ? lastFrame.getStack(i) : null;
					val stackValue = firstFrame.getStack(i);

					// FIXME: merged values which may sometimes be uninitialised are treated as initialised here?
					// can this ever happen for stack values?
					if (Objects.equals(stackValue, origStackValue))
						continue;

					results.add(new IntermediateValue(new Type(stackValue.getType().getDescriptor()), stackValue.getConstantValue(), new IntermediateValue.Location(STACK, i, null)));
				}
			}

			return results;
		}

		protected void setUsedLocalIndexes(BitSet usedLocals) {
		}

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
				//clonedMethod.maxLocals += 20;
				//clonedMethod.maxStack += 20;
				fragment = new MethodNodeInfoCodeFragment(containingMethodNodeInfo.getClassInfo().wrap(clonedMethod));
				DebugPrinter.printByteCode(clonedMethod, "base");
				applyInsertionOptions((MethodNodeInfoCodeFragment) fragment, insertionOptions);
				DebugPrinter.printByteCode(clonedMethod, "insertionOptions");
				convertTypes((MethodNodeInfoCodeFragment) fragment, position);
				DebugPrinter.printByteCode(clonedMethod, "convertedTypes");
			}

			val insertedExecutionResult = fragment.getExecutionOutcome();

			if (!executionResult.canFallThrough && insertedExecutionResult.canFallThrough)
				throw new UnreachableInsertionException(this, InsertionPosition.AFTER);

			val startIndex = containingList.indexOf(first);
			val endIndex = containingList.indexOf(last);
			if (startIndex == -1 || endIndex == -1)
				throw new ArrayIndexOutOfBoundsException();

			// optional: convert method returns
			// TODO: inputTypes should match
			{
				val ourInputTypes = getInputTypes();

				//if (!ourInputTypes.equals(inputTypes))
				//	throw new IllegalArgumentException("mismatch of fragment types: \n" + ourInputTypes + '\n' + inputTypes);
			}

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

			if (existingInputTypes.size() != index)
				throw new IllegalStateException("size mismatch: existing input types size does not match required input type size");

			rebaseLocals(locals, insertFragment, containingMethodNodeInfo.node.maxLocals);
			insns.insert(varInsns);

			//insns.insert(lastVarAdded);
			if (existingOutputTypes == existingInputTypes && outputTypes.isEmpty())
				for (val iv : existingOutputTypes) {
					if (iv.location.type != STACK)
						continue;
					localIndex--;
					if (localIndex < 0)
						throw new IllegalStateException("localIndex < 0");
					val stackIndex = iv.location.index;
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
						val prev = current.getPrevious();
						insns.remove(current);
						containingMethodNodeInfo.markCodeDirty();
					}
				}
			}
			if (options.convertReturnCallToReturnInstruction) {
				// TODO: implement this
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
		public InstructionCodeFragment(ByteCodeInfo.MethodNodeInfo containingMethodNodeInfo) {
			super(containingMethodNodeInfo);
		}

		public abstract AbstractInsnNode getInstruction();

		@Override
		public final AbstractInsnNode getFirstInstruction() {
			return getInstruction();
		}

		@Override
		public final AbstractInsnNode getLastInstruction() {
			return getInstruction();
		}
	}

	static class MethodNodeInfoCodeFragment extends AsmCodeFragment implements CodeFragment.Body {
		public MethodNodeInfoCodeFragment(ByteCodeInfo.MethodNodeInfo containingMethodNodeInfo) {
			super(containingMethodNodeInfo);
		}

		@Override
		protected void setUsedLocalIndexes(BitSet set) {
			int i = 0;

			if (!containingMethodNodeInfo.getAccessFlags().has(AccessFlags.ACC_STATIC))
				set.set(i++);

			for (Parameter parameter : containingMethodNodeInfo.getParameters())
				set.set(i++);

			if (i > containingMethodNodeInfo.node.maxLocals)
				throw new IllegalStateException();
		}

		@Override
		public AbstractInsnNode getFirstInstruction() {
			return containingMethodNodeInfo.node.instructions.getFirst();
		}

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

		@Override
		public Type getContainingClassType() {
			return new Type('L' + instruction.owner + ';');
		}

		@Override
		public String getName() {
			return instruction.name;
		}
	}
}
