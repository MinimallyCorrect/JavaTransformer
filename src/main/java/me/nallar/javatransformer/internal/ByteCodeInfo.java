package me.nallar.javatransformer.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.val;
import me.nallar.javatransformer.api.*;
import me.nallar.javatransformer.internal.util.AnnotationParser;
import me.nallar.javatransformer.internal.util.CachingSupplier;
import me.nallar.javatransformer.internal.util.Cloner;
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
		MethodNode node;
		if (method instanceof MethodNodeInfo) {
			node = Cloner.clone(((MethodNodeInfo) method).node);
		} else {
			node = new MethodNode();
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
			node = Cloner.clone(((FieldNodeInfo) field).node);
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
			throw new TransformationException("Method " + method + " can not be removed as it is not present");

		node.get().methods.remove(methodNodeInfo.node);
	}

	@Override
	public void remove(FieldInfo field) {
		FieldNodeInfo fieldNodeInfo = !(field instanceof FieldNodeInfo) ? (FieldNodeInfo) get(field) : (FieldNodeInfo) field;

		if (fieldNodeInfo == null)
			throw new TransformationException("Field " + field + " can not be removed as it is not present");

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

		@Override
		public String toString() {
			return SimpleFieldInfo.toString(this);
		}

		@Override
		@SuppressWarnings("MethodDoesntCallSuperMethod")
		public FieldInfo clone() {
			return new FieldNodeInfo(Cloner.clone(node));
		}
	}

	class MethodNodeInfo implements MethodInfo {
		private final MethodNode node;
		private CachingSupplier<MethodDescriptor> descriptor;

		MethodNodeInfo(MethodNode node) {
			this.node = node;
			descriptor = CachingSupplier.of(() -> {
				try {
					return new MethodDescriptor(node.desc, node.signature);
				} catch (TransformationException e) {
					throw new TransformationException("Failed to parse method parameters in " + node.name + ':' +
						"\n\tname: " + node.name +
						"\n\tdescriptor: " + node.desc +
						"\n\tsignature:" + node.signature, e);
				}
			});
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
			return descriptor.get().getReturnType();
		}

		@Override
		public void setReturnType(Type returnType) {
			descriptor.set(descriptor.get().withReturnType(returnType));
			descriptor.get().saveTo(node);
		}

		@Override
		public List<Parameter> getParameters() {
			return descriptor.get().getParameters();
		}

		@Override
		public void setParameters(List<Parameter> parameters) {
			descriptor.set(descriptor.get().withParameters(parameters));
			descriptor.get().saveTo(node);
		}

		public String getDescriptor() {
			return descriptor.get().getDescriptor();
		}

		@Override
		public List<Annotation> getAnnotations() {
			return CollectionUtil.union(node.invisibleAnnotations, node.visibleAnnotations).map(AnnotationParser::annotationFromAnnotationNode).collect(Collectors.toList());
		}

		@Override
		public ClassInfo getClassInfo() {
			return ByteCodeInfo.this;
		}

		@Override
		public String toString() {
			return SimpleMethodInfo.toString(this);
		}

		@Override
		public List<TypeVariable> getTypeVariables() {
			return descriptor.get().getTypeVariables();
		}

		@Override
		public void setTypeVariables(List<TypeVariable> typeVariables) {
			descriptor.set(descriptor.get().withTypeVariables(typeVariables));
			descriptor.get().saveTo(node);
		}

		@Override
		@SuppressWarnings("MethodDoesntCallSuperMethod")
		public MethodInfo clone() {
			return new MethodNodeInfo(Cloner.clone(node));
		}
	}
}
