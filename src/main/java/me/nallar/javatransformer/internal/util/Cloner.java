package me.nallar.javatransformer.internal.util;

import lombok.experimental.UtilityClass;
import lombok.val;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

@UtilityClass
public class Cloner {
	public FieldNode clone(FieldNode other) {
		val node = new FieldNode(other.access, other.name, other.desc, other.signature, other.value);
		node.attrs = other.attrs;
		node.invisibleAnnotations = other.invisibleAnnotations;
		node.invisibleTypeAnnotations = other.invisibleTypeAnnotations;
		node.visibleAnnotations = other.visibleAnnotations;
		node.visibleTypeAnnotations = other.visibleTypeAnnotations;
		return node;
	}

	public MethodNode clone(MethodNode other) {
		val node = new MethodNode();
		node.desc = other.desc;
		node.signature = other.signature;
		node.access = other.access;
		node.name = other.name;
		node.attrs = other.attrs;
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
}
