package org.minimallycorrect.javatransformer.internal;

import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.val;
import org.minimallycorrect.javatransformer.api.*;
import org.minimallycorrect.javatransformer.internal.util.AnnotationParser;
import org.minimallycorrect.javatransformer.internal.util.CachingSupplier;
import org.minimallycorrect.javatransformer.internal.util.TypeUtil;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

@Getter
@ToString
public class MethodDescriptor {
	private final List<TypeVariable> typeVariables;
	private final List<Parameter> parameters;
	private final Type returnType;

	public MethodDescriptor(List<TypeVariable> typeVariables, List<Parameter> parameters, Type returnType) {
		this.typeVariables = typeVariables;
		this.parameters = parameters;
		this.returnType = returnType;
	}

	public MethodDescriptor(@NonNull String descriptor, String signature) {
		this(descriptor, signature, null);
	}

	public MethodDescriptor(MethodNode node) {
		this(getTypeVariables(node.signature), getParameters(node), getReturnType(node.desc, node.signature));
	}

	public MethodDescriptor(String descriptor, String signature, List<String> parameterNames) {
		this(getTypeVariables(signature), getParameters(descriptor, signature, parameterNames, null, null), getReturnType(descriptor, signature));
	}

	private static List<TypeVariable> getTypeVariables(String signature) {
		if (signature == null)
			return Collections.emptyList();

		String before = before('(', signature);
		String typeArguments = ResolutionContext.extractGeneric(before);

		if (typeArguments == null)
			return Collections.emptyList();

		val list = new ArrayList<TypeVariable>();
		int pos = 0;
		int start = 0;
		while (pos < typeArguments.length()) {
			char c = typeArguments.charAt(pos++);
			switch (c) {
				case ':':
					String name = typeArguments.substring(start, pos);
					String bounds = TypeUtil.readType(typeArguments, pos, true);
					pos += bounds.length();
					list.add(new TypeVariable(name, Type.ofSignature(bounds)));
			}
		}

		return list;
	}

	private static Type getReturnType(String descriptor, String signature) {
		String returnDescriptor = after(')', descriptor);
		String returnSignature = null;

		if (signature != null)
			returnSignature = after(')', signature);

		return new Type(returnDescriptor, returnSignature);
	}

	private static List<Parameter> getParameters(MethodNode node) {
		val parameterNames = new ArrayList<String>();
		if (node.parameters != null)
			for (val param : node.parameters)
				parameterNames.add(param.name);
		return getParameters(node.desc, node.signature, parameterNames, node.invisibleParameterAnnotations, node.visibleParameterAnnotations);
	}

	private static List<Parameter> getParameters(String descriptor, String signature, List<String> parameterNames, List<AnnotationNode>[] invisibleAnnotations, List<AnnotationNode>[] visibleAnnotations) {
		val parameters = new ArrayList<Parameter>();

		List<Type> parameterTypes = Type.listOf(getParameters(descriptor), getParameters(signature));

		for (int i = 0; i < parameterTypes.size(); i++) {
			String name = (parameterNames == null || i >= parameterNames.size()) ? null : parameterNames.get(i);
			CachingSupplier<List<Annotation>> annotationSupplier = null;

			if ((invisibleAnnotations != null && invisibleAnnotations.length > 0) || (visibleAnnotations != null && visibleAnnotations.length > 0)) {
				val j = i;
				annotationSupplier = CachingSupplier.of(() -> {
					val annotations = new ArrayList<Annotation>();
					if (invisibleAnnotations != null && j < invisibleAnnotations.length)
						for (val node : invisibleAnnotations[j])
							annotations.add(AnnotationParser.annotationFromAnnotationNode(node));
					if (visibleAnnotations != null && j < visibleAnnotations.length)
						for (val node : visibleAnnotations[j])
							annotations.add(AnnotationParser.annotationFromAnnotationNode(node));
					return annotations;
				});
			}
			parameters.add(Parameter.of(parameterTypes.get(i), name, annotationSupplier));
		}

		return parameters;
	}

	private static String getParameters(String descriptor) {
		if (descriptor == null)
			return null;
		return before(')', after('(', descriptor));
	}

	private static String before(char c, String in) {
		int index = in.indexOf(c);

		if (index == -1)
			throw new TransformationException("Could not find '" + c + "' in '" + in + "'");

		return in.substring(0, index);
	}

	private static String after(char c, String in) {
		int index = in.indexOf(c);

		if (index == -1)
			throw new TransformationException("Could not find '" + c + "' in '" + in + "'");

		return in.substring(index + 1, in.length());
	}

	public MethodDescriptor withTypeVariables(List<TypeVariable> typeVariables) {
		return new MethodDescriptor(typeVariables, parameters, returnType);
	}

	public MethodDescriptor withParameters(List<Parameter> parameters) {
		return new MethodDescriptor(typeVariables, parameters, returnType);
	}

	public MethodDescriptor withReturnType(Type returnType) {
		return new MethodDescriptor(typeVariables, parameters, returnType);
	}

	public void saveTo(MethodNode node) {
		node.desc = getDescriptor();
		node.signature = getSignature();
	}

	public String getDescriptor() {
		StringBuilder desc = new StringBuilder("(");

		for (Parameter parameter : parameters) {
			desc.append(parameter.type.descriptor);
		}

		desc.append(")").append(returnType.descriptor);

		return desc.toString();
	}

	private String getSignature() {
		boolean any = false;
		StringBuilder signature = new StringBuilder();

		val typeVariables = getTypeVariables();

		if (!typeVariables.isEmpty()) {
			signature.append('<');
			typeVariables.forEach(signature::append);
			signature.append('>');
		}

		signature.append("(");

		for (Parameter parameter : parameters) {
			String generic = parameter.type.signature;
			if (generic == null)
				generic = parameter.type.descriptor;
			else
				any = true;

			signature.append(generic);
		}

		signature.append(")");
		String generic = returnType.signature;
		if (generic == null)
			generic = returnType.descriptor;
		else
			any = true;

		signature.append(generic);

		if (any)
			return signature.toString();

		return null;
	}
}
