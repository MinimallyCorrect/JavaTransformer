package me.nallar.javatransformer.internal.util;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.TypeParameter;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.function.*;

@UtilityClass
public class NodeUtil {
	public static <ResultType> List<ResultType> getFromList(Node node, Function<Node, List<ResultType>> getter) {
		List<ResultType> parameters = new ArrayList<>();

		while (true) {
			if (node == null)
				return parameters;

			List<ResultType> extra = getter.apply(node);
			if (extra != null && !extra.isEmpty()) {
				parameters.addAll(extra);
			}

			node = node.getParentNode();
		}
	}

	public static <ResultType> List<ResultType> getFromSingle(Node node, Function<Node, ResultType> getter) {
		List<ResultType> parameters = null;

		while (true) {
			if (node == null)
				return parameters;

			ResultType extra = getter.apply(node);
			if (extra != null) {
				if (parameters == null) {
					parameters = new ArrayList<>();
				}

				parameters.add(extra);
			}

			node = node.getParentNode();
		}
	}

	public static List<TypeParameter> getTypeParameters(Node node) {
		return getFromList(node, NodeUtil::getTypeParametersOnly);
	}

	private static List<TypeParameter> getTypeParametersOnly(Node node) {
		if (node instanceof ClassOrInterfaceDeclaration) {
			return ((ClassOrInterfaceDeclaration) node).getTypeParameters();
		}

		if (node instanceof MethodDeclaration) {
			return ((MethodDeclaration) node).getTypeParameters();
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T extends Node> T getParentNode(Node node, Class<T> target) {
		while (true) {
			node = node.getParentNode();
			if (node == null || target.isAssignableFrom(target.getClass())) {
				return (T) node;
			}
		}
	}
}
