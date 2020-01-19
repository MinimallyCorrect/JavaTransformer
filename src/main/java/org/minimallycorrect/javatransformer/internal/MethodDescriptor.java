package org.minimallycorrect.javatransformer.internal;

import java.util.List;

import lombok.NonNull;
import lombok.ToString;
import lombok.val;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.MethodNode;

import org.minimallycorrect.javatransformer.api.Parameter;
import org.minimallycorrect.javatransformer.api.Type;
import org.minimallycorrect.javatransformer.api.TypeVariable;

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

	public MethodDescriptor(@NonNull String descriptor, @Nullable String signature) {
		this(descriptor, signature, null);
	}

	public MethodDescriptor(MethodNode node) {
		this(Signature.getTypeVariables(node.signature), Signature.getParameters(node), Signature.getReturnType(node.desc, node.signature));
	}

	public MethodDescriptor(String descriptor, @Nullable String signature, @Nullable List<String> parameterNames) {
		this(Signature.getTypeVariables(signature), Signature.getParameters(descriptor, signature, parameterNames, null, null), Signature.getReturnType(descriptor, signature));
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

	@Nullable
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

	public List<TypeVariable> getTypeVariables() {
		return this.typeVariables;
	}

	public List<Parameter> getParameters() {
		return this.parameters;
	}

	public Type getReturnType() {
		return this.returnType;
	}
}
