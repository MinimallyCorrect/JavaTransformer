package org.minimallycorrect.javatransformer.internal;

import lombok.Getter;
import lombok.ToString;
import lombok.val;
import org.minimallycorrect.javatransformer.api.Parameter;
import org.minimallycorrect.javatransformer.api.TransformationException;
import org.minimallycorrect.javatransformer.api.Type;
import org.minimallycorrect.javatransformer.api.TypeVariable;
import org.minimallycorrect.javatransformer.internal.util.TypeUtil;
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

	public MethodDescriptor(String descriptor, String signature) {
		this(descriptor, signature, null);
	}

	public MethodDescriptor(String descriptor, String signature, List<String> parameterNames) {
		this(getTypeVariables(signature), getParameters(descriptor, signature, parameterNames), getReturnType(descriptor, signature));
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

	private static List<Parameter> getParameters(String descriptor, String signature, List<String> parameterNames) {
		val parameters = new ArrayList<Parameter>();

		List<Type> parameterTypes = Type.listOf(getParameters(descriptor), getParameters(signature));

		for (int i = 0; i < parameterTypes.size(); i++) {
			String name = (parameterNames == null || parameterNames.isEmpty()) ? null : parameterNames.get(i);
			parameters.add(new Parameter(parameterTypes.get(i), name));
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
			desc.append(parameter.descriptor);
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
			String generic = parameter.signature;
			if (generic == null)
				generic = parameter.descriptor;
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
