package me.nallar.javatransformer.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.val;
import me.nallar.javatransformer.api.*;
import me.nallar.javatransformer.internal.util.AnnotationParser;
import me.nallar.javatransformer.internal.util.CollectionUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

@Data
@AllArgsConstructor
@SuppressWarnings("unchecked")
public class ByteCodeInfo implements ClassInfoStreams {
	private final Supplier<ClassNode> node;
	@Getter(lazy = true)
	private final List<Annotation> annotations = getAnnotationsInternal();
	private String className;

	@Override
	public String getName() {
		return className;
	}

	@Override
	public void setName(String name) {
		className = name;
		node.get().name = name.replace('.', '/');
	}

	@Override
	public AccessFlags getAccessFlags() {
		return new AccessFlags(node.get().access);
	}

	@Override
	public void setAccessFlags(AccessFlags accessFlags) {
		node.get().access = accessFlags.access;
	}

	public void add(MethodInfo method) {
		MethodNode node = new MethodNode();
		if (method instanceof MethodNodeInfo) {
			val other = ((MethodNodeInfo) method).node;
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
		} else {
			node.desc = "()V";
			node.exceptions = new ArrayList<>();
			MethodInfo info = new MethodNodeInfo(node);
			info.setAll(method);
		}
		this.node.get().methods.add(node);
	}

	public void add(FieldInfo field) {
		FieldNode node;
		if (field instanceof FieldNodeInfo) {
			val other = ((FieldNodeInfo) field).node;
			node = new FieldNode(other.access, other.name, other.desc, other.signature, other.value);
			node.attrs = other.attrs;
			node.invisibleAnnotations = other.invisibleAnnotations;
			node.invisibleTypeAnnotations = other.invisibleTypeAnnotations;
			node.visibleAnnotations = other.visibleAnnotations;
			node.visibleTypeAnnotations = other.visibleTypeAnnotations;
		} else {
			node = new FieldNode(0, null, "V", null, null);
			val nodeInfo = new FieldNodeInfo(node);
			nodeInfo.setAll(field);
		}
		this.node.get().fields.add(node);
	}

	@Override
	public void remove(MethodInfo method) {
		MethodNodeInfo methodNodeInfo = !(method instanceof MethodNodeInfo) ? (MethodNodeInfo) get(method) : (MethodNodeInfo) method;

		if (methodNodeInfo == null)
			throw new RuntimeException("Method " + method + " can not be removed as it is not present");

		node.get().methods.remove(methodNodeInfo.node);
	}

	@Override
	public void remove(FieldInfo field) {
		FieldNodeInfo fieldNodeInfo = !(field instanceof FieldNodeInfo) ? (FieldNodeInfo) get(field) : (FieldNodeInfo) field;

		if (fieldNodeInfo == null)
			throw new RuntimeException("Field " + field + " can not be removed as it is not present");

		node.get().fields.remove(fieldNodeInfo.node);
	}

	@Override
	public Type getSuperType() {
		return new Type("L" + node.get().superName + ";");
	}

	@Override
	public List<Type> getInterfaceTypes() {
		return node.get().interfaces.stream().map((it) -> new Type("L" + it + ";")).collect(Collectors.toList());
	}

	public Stream<MethodInfo> getMethodStream() {
		return node.get().methods.stream().map(MethodNodeInfo::new);
	}

	public Stream<FieldInfo> getFieldStream() {
		return node.get().fields.stream().map(FieldNodeInfo::new);
	}

	private List<Annotation> getAnnotationsInternal() {
		return CollectionUtil.union(node.get().invisibleAnnotations, node.get().visibleAnnotations).map(AnnotationParser::annotationFromAnnotationNode).collect(Collectors.toList());
	}

	@Override
	public ClassInfo getClassInfo() {
		return this;
	}

	MethodInfo wrap(MethodNode node) {
		return new MethodNodeInfo(node);
	}

	class FieldNodeInfo implements FieldInfo {
		public final FieldNode node;
		private Type type;

		FieldNodeInfo(FieldNode node) {
			this.node = node;
			type = new Type(node.desc, node.signature);
		}

		@Override
		public String getName() {
			return node.name;
		}

		@Override
		public void setName(String name) {
			node.name = name;
		}

		@Override
		public AccessFlags getAccessFlags() {
			return new AccessFlags(node.access);
		}

		@Override
		public void setAccessFlags(AccessFlags accessFlags) {
			node.access = accessFlags.access;
		}

		@Override
		public Type getType() {
			return type;
		}

		@Override
		public void setType(Type type) {
			this.type = type;
			node.desc = type.descriptor;
			node.signature = type.signature;
		}

		@Override
		public List<Annotation> getAnnotations() {
			return CollectionUtil.union(node.invisibleAnnotations, node.visibleAnnotations).map(AnnotationParser::annotationFromAnnotationNode).collect(Collectors.toList());
		}

		@Override
		public ClassInfo getClassInfo() {
			return ByteCodeInfo.this;
		}
	}

	class MethodNodeInfo implements MethodInfo {
		private final MethodNode node;
		private MethodDescriptor descriptor;

		MethodNodeInfo(MethodNode node) {
			this.node = node;
			descriptor = new MethodDescriptor(node.desc, node.signature);
		}

		@Override
		public AccessFlags getAccessFlags() {
			return new AccessFlags(node.access);
		}

		@Override
		public void setAccessFlags(AccessFlags accessFlags) {
			node.access = accessFlags.access;
		}

		@Override
		public String getName() {
			return node.name;
		}

		@Override
		public void setName(String name) {
			node.name = name;
		}

		@Override
		public Type getReturnType() {
			return new MethodDescriptor(node.desc, node.signature).getReturnType();
		}

		@Override
		public void setReturnType(Type returnType) {
			descriptor = descriptor.withReturnType(returnType);
			descriptor.saveTo(node);
		}

		@Override
		public List<Parameter> getParameters() {
			val descriptor = new MethodDescriptor(node.desc, node.signature);
			return descriptor.getParameters();
		}

		@Override
		public void setParameters(List<Parameter> parameters) {
			descriptor = descriptor.withParameters(parameters);
			descriptor.saveTo(node);
		}

		public String getDescriptor() {
			return descriptor.getDescriptor();
		}

		@Override
		public List<Annotation> getAnnotations() {
			return CollectionUtil.union(node.invisibleAnnotations, node.visibleAnnotations).map(AnnotationParser::annotationFromAnnotationNode).collect(Collectors.toList());
		}

		@Override
		public ClassInfo getClassInfo() {
			return ByteCodeInfo.this;
		}
	}
}
