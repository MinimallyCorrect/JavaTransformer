package dev.minco.javatransformer.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.val;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

import dev.minco.javatransformer.api.Annotation;
import dev.minco.javatransformer.api.Parameter;
import dev.minco.javatransformer.api.TransformationException;
import dev.minco.javatransformer.api.Type;
import dev.minco.javatransformer.api.TypeVariable;
import dev.minco.javatransformer.internal.util.AnnotationParser;
import dev.minco.javatransformer.internal.util.CachingSupplier;
import dev.minco.javatransformer.internal.util.TypeUtil;

public final class Signature {
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

	static List<TypeVariable> getTypeVariables(@Nullable String signature) {
		if (signature == null)
			return Collections.emptyList();

		if (signature.indexOf('(') != -1) {
			signature = before('(', signature);
		}
		String typeArguments = ResolutionContext.extractGeneric(signature);

		if (typeArguments == null)
			return Collections.emptyList();

		val list = new ArrayList<TypeVariable>();
		int pos = 0;
		int start = 0;
		val len = typeArguments.length();

		// FIXME very broken needs some thorough unit tests
		while (pos < len) {
			char c = typeArguments.charAt(pos++);
			switch (c) {
				case ':':
					String name = typeArguments.substring(start, pos - 1);
					// TODO implicit object bound?
					int offset = typeArguments.charAt(pos) == ':' ? 1 : 0;
					String bounds = TypeUtil.readType(typeArguments, pos + offset, true);
					pos += bounds.length();
					list.add(new TypeVariable(name, Type.ofSignature(bounds)));
					if (pos < len && typeArguments.charAt(pos) != ':') {
						start = pos;
					}
			}
		}

		return list;
	}

	static Type getReturnType(String descriptor, @Nullable String signature) {
		String returnDescriptor = after(')', descriptor);
		String returnSignature = null;

		if (signature != null)
			returnSignature = after(')', signature);

		return new Type(returnDescriptor, returnSignature);
	}

	static List<Parameter> getParameters(MethodNode node) {
		val parameterNames = new ArrayList<String>();
		if (node.parameters != null)
			for (val param : node.parameters)
				parameterNames.add(param.name);
		return getParameters(node.desc, node.signature, parameterNames, node.invisibleParameterAnnotations, node.visibleParameterAnnotations);
	}

	static List<Parameter> getParameters(String descriptor, @Nullable String signature, @Nullable List<String> parameterNames, @Nullable List<AnnotationNode>[] invisibleAnnotations, @Nullable List<AnnotationNode>[] visibleAnnotations) {
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
						//noinspection ConstantConditions
						for (val node : invisibleAnnotations[j])
							annotations.add(AnnotationParser.annotationFromAnnotationNode(node));
					if (visibleAnnotations != null && j < visibleAnnotations.length)
						//noinspection ConstantConditions
						for (val node : visibleAnnotations[j])
							annotations.add(AnnotationParser.annotationFromAnnotationNode(node));
					return annotations;
				});
			}
			parameters.add(Parameter.of(parameterTypes.get(i), name, annotationSupplier));
		}

		return parameters;
	}

	@Nullable
	private static String getParameters(String descriptor) {
		if (descriptor == null)
			return null;
		return before(')', after('(', descriptor));
	}
}
