package org.minimallycorrect.javatransformer.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Frame;

import org.minimallycorrect.javatransformer.api.AccessFlags;
import org.minimallycorrect.javatransformer.api.Annotation;
import org.minimallycorrect.javatransformer.api.ClassInfo;
import org.minimallycorrect.javatransformer.api.FieldInfo;
import org.minimallycorrect.javatransformer.api.MethodInfo;
import org.minimallycorrect.javatransformer.api.Parameter;
import org.minimallycorrect.javatransformer.api.TransformationException;
import org.minimallycorrect.javatransformer.api.Type;
import org.minimallycorrect.javatransformer.api.TypeVariable;
import org.minimallycorrect.javatransformer.api.code.CodeFragment;
import org.minimallycorrect.javatransformer.internal.asm.CombinedAnalyzer;
import org.minimallycorrect.javatransformer.internal.asm.CombinedInterpreter;
import org.minimallycorrect.javatransformer.internal.asm.CombinedValue;
import org.minimallycorrect.javatransformer.internal.asm.FilteringClassWriter;
import org.minimallycorrect.javatransformer.internal.util.AnnotationParser;
import org.minimallycorrect.javatransformer.internal.util.CachingSupplier;
import org.minimallycorrect.javatransformer.internal.util.Cloner;
import org.minimallycorrect.javatransformer.internal.util.CollectionUtil;

@Data
@SuppressWarnings("unchecked")
public class ByteCodeInfo implements ClassInfo {
	private final Supplier<ClassNode> node;
	@Getter(lazy = true)
	private final List<Annotation> annotations = getAnnotationsInternal();
	public boolean hasChangedMethodControlFlow;
	@NonNull
	private String className;
	@NonNull
	private Map<String, String> filters;

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
			val orig = ((MethodNodeInfo) method);
			node = Cloner.clone(orig.node);
			FilteringClassWriter.addFilter(filters, orig.getClassInfo().getName(), getName());
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

	public Stream<MethodInfo> getMethods() {
		return node.get().methods.stream().map(MethodNodeInfo::new);
	}

	public Stream<FieldInfo> getFields() {
		return node.get().fields.stream().map(FieldNodeInfo::new);
	}

	private List<Annotation> getAnnotationsInternal() {
		// WAT: splitting this up from a single statement fixed the failure at runtime
		// was originally:
		// return CollectionUtil.union(node.get().invisibleAnnotations, node.get().visibleAnnotations).map(AnnotationParser::annotationFromAnnotationNode).collect(Collectors.toList());
		// Was failing at runtime with 'java.lang.BootstrapMethodError: java.lang.IllegalAccessError:' with no message
		// Split up into multiple lines to try to help diagnose.
		val allAnnotationNodes = CollectionUtil.union(node.get().invisibleAnnotations, node.get().visibleAnnotations);
		//noinspection Convert2MethodRef
		val stream = allAnnotationNodes.map((it) -> AnnotationParser.annotationFromAnnotationNode(it));
		return stream.collect(Collectors.toList());
	}

	MethodNodeInfo wrap(MethodNode node) {
		return new MethodNodeInfo(node);
	}

	public class FieldNodeInfo implements FieldInfo {
		final FieldNode node;
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

	public class MethodNodeInfo implements MethodInfo {
		public final MethodNode node;
		private final CachingSupplier<Frame<CombinedValue>[]> stackFrames;
		private final CachingSupplier<MethodDescriptor> descriptor;
		private final CachingSupplier<CodeFragment.Body> codeFragment;

		MethodNodeInfo(MethodNode node) {
			this.node = node;
			descriptor = CachingSupplier.of(() -> {
				try {
					return new MethodDescriptor(node);
				} catch (TransformationException e) {
					throw new TransformationException("Failed to parse method parameters in " + node.name + ':' +
						"\n\tname: " + node.name +
						"\n\tdescriptor: " + node.desc +
						"\n\tsignature:" + node.signature, e);
				}
			});
			codeFragment = CachingSupplier.of(() -> new AsmCodeFragmentGenerator.MethodNodeInfoCodeFragment(this));
			stackFrames = CachingSupplier.of(this::analyzeStackFrames);
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
		public ByteCodeInfo getClassInfo() {
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

		@Override
		public @NonNull CodeFragment.Body getCodeFragment() {
			return codeFragment.get();
		}

		public Frame<CombinedValue>[] getStackFrames() {
			return stackFrames.get();
		}

		@SneakyThrows
		private Frame<CombinedValue>[] analyzeStackFrames() {
			return CombinedAnalyzer.analyze(new CombinedInterpreter(), getClassInfo().getNode().get().name, node);
		}

		public void markCodeDirty() {
			stackFrames.set(null);
			hasChangedMethodControlFlow = true;
		}
	}
}
