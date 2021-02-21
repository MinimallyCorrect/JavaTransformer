package dev.minco.javatransformer.internal.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import lombok.NonNull;
import lombok.val;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import dev.minco.javatransformer.api.TransformationException;

public final class Cloner {
	public static FieldNode clone(FieldNode other) {
		val node = new FieldNode(other.access, other.name, other.desc, other.signature, other.value);
		node.attrs = other.attrs;
		node.invisibleAnnotations = other.invisibleAnnotations;
		node.invisibleTypeAnnotations = other.invisibleTypeAnnotations;
		node.visibleAnnotations = other.visibleAnnotations;
		node.visibleTypeAnnotations = other.visibleTypeAnnotations;
		return node;
	}

	public static MethodNode clone(MethodNode other) {
		val node = new MethodNode();
		node.desc = other.desc;
		node.signature = other.signature;
		node.access = other.access;
		node.name = other.name;
		node.attrs = other.attrs;
		node.maxLocals = other.maxLocals;
		node.maxStack = other.maxStack;
		node.annotationDefault = other.annotationDefault;
		node.exceptions = other.exceptions;
		node.instructions = other.instructions;
		node.invisibleAnnotations = other.invisibleAnnotations;
		node.invisibleLocalVariableAnnotations = other.invisibleLocalVariableAnnotations;
		node.invisibleParameterAnnotations = other.invisibleParameterAnnotations;
		node.invisibleTypeAnnotations = other.invisibleTypeAnnotations;
		node.visibleAnnotations = other.visibleAnnotations;
		node.visibleLocalVariableAnnotations = other.visibleLocalVariableAnnotations;
		node.visibleParameterAnnotations = other.visibleParameterAnnotations;
		node.visibleTypeAnnotations = other.visibleTypeAnnotations;
		node.localVariables = other.localVariables;
		node.tryCatchBlocks = other.tryCatchBlocks;
		return node;
	}

	public static MethodNode deepClone(MethodNode other) {
		val node = new MethodNode();
		node.desc = other.desc;
		node.signature = other.signature;
		node.access = other.access;
		node.name = other.name;
		node.attrs = clone(other.attrs);
		node.maxLocals = other.maxLocals;
		node.maxStack = other.maxStack;
		node.annotationDefault = other.annotationDefault;
		node.exceptions = clone(other.exceptions);
		node.instructions = other.instructions;
		node.invisibleAnnotations = clone(other.invisibleAnnotations);
		node.invisibleLocalVariableAnnotations = clone(other.invisibleLocalVariableAnnotations);
		node.invisibleParameterAnnotations = clone(other.invisibleParameterAnnotations);
		node.invisibleTypeAnnotations = clone(other.invisibleTypeAnnotations);
		node.visibleAnnotations = clone(other.visibleAnnotations);
		node.visibleLocalVariableAnnotations = clone(other.visibleLocalVariableAnnotations);
		node.visibleParameterAnnotations = clone(other.visibleParameterAnnotations);
		node.visibleTypeAnnotations = clone(other.visibleTypeAnnotations);
		node.localVariables = clone(other.localVariables);
		node.tryCatchBlocks = clone(other.tryCatchBlocks);
		return node;
	}

	@Contract(value = "null -> null; !null -> !null", pure = true)
	@Nullable
	private static <T> ArrayList<T> clone(@Nullable List<T> list) {
		return list == null ? null : new ArrayList<>(list);
	}

	@Contract(value = "null -> null; !null -> !null", pure = true)
	@Nullable
	@SuppressWarnings("unchecked")
	private static <T> ArrayList<T>[] clone(@Nullable List<T>[] lists) {
		if (lists == null)
			return null;
		val newList = new List<?>[lists.length];
		for (int i = 0; i < lists.length; i++)
			newList[i] = clone(lists[i]);
		return (ArrayList<T>[]) newList;
	}

	public static InsnList clone(InsnList list) {
		return clone(list, list.getFirst(), list.getLast());
	}

	public static InsnList clone(@NonNull InsnList list, @Nullable AbstractInsnNode first, @Nullable AbstractInsnNode last) {
		val cloned = new InsnList();
		if (first == null && last == null)
			return cloned;
		if (first == null || last == null)
			throw new NullPointerException();

		val labels = new HashMap<LabelNode, LabelNode>() {
			@Override
			public LabelNode get(Object key) {
				val result = super.get(key);
				if (result == null)
					throw new TransformationException("Label " + key + " referenced by copied instruction in " + list + " is out of bounds to be copied " + first + "->" + last);
				return result;
			}
		};

		AbstractInsnNode insn = first;
		while (true) {
			if ((insn instanceof LabelNode))
				labels.put((LabelNode) insn, new LabelNode());
			if (insn == last)
				break;
			insn = insn.getNext();
		}
		insn = first;
		while (true) {
			cloned.add(insn.clone(labels));
			if (insn == last)
				break;
			insn = insn.getNext();
		}

		return cloned;
	}
}
