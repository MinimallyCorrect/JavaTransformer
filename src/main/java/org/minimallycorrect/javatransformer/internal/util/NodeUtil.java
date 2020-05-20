package org.minimallycorrect.javatransformer.internal.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.val;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.TypeParameter;

public final class NodeUtil {
	public static void forChildren(Node node, Consumer<Node> nodeConsumer) {
		forChildren(node, nodeConsumer, Node.class);
	}

	@SuppressWarnings("unchecked")
	public static <T> void forChildren(Node node, Consumer<T> nodeConsumer, Class<T> ofClass) {
		for (Node child : node.getChildNodes()) {
			if (ofClass.isInstance(child))
				nodeConsumer.accept((T) child);

			forChildren(child, nodeConsumer, ofClass);
		}
	}

	public static <ResultType> List<ResultType> getFromList(Node node, Function<Node, List<ResultType>> getter) {
		List<ResultType> parameters = new ArrayList<>();

		while (true) {
			if (node == null)
				return parameters;

			List<ResultType> extra = getter.apply(node);
			if (extra != null && !extra.isEmpty()) {
				parameters.addAll(extra);
			}

			node = node.getParentNode().orElse(null);
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

			node = node.getParentNode().orElse(null);
		}
	}

	public static List<TypeParameter> getTypeParameters(Node node) {
		return getFromList(node, NodeUtil::getTypeParametersOnly);
	}

	private static NodeList<TypeParameter> getTypeParametersOnly(Node node) {
		if (node instanceof NodeWithTypeParameters) {
			return ((NodeWithTypeParameters<?>) node).getTypeParameters();
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T extends Node> T getParentNode(Node node, Class<T> target) {
		while (true) {
			node = node.getParentNode().orElse(null);
			if (node == null || target.isAssignableFrom(node.getClass())) {
				return (T) node;
			}
		}
	}

	public static String qualifiedName(Name name) {
		return name.asString();
	}

	public static <T extends Node> List<T> findWithinMethodScope(Class<T> clazz, Node node) {
		val list = new ArrayList<T>();
		findScopedLocals(clazz, node, list);
		return list;
	}

	@SuppressWarnings("unchecked")
	private static <T> void findScopedLocals(Class<T> clazz, Node node, List<T> results) {
		for (Node childNode : node.getChildNodes()) {
			if (childNode instanceof LambdaExpr || childNode instanceof ObjectCreationExpr) {
				continue;
			}
			if (clazz.isAssignableFrom(childNode.getClass())) {
				results.add((T) childNode);
			}
			findScopedLocals(clazz, childNode, results);
		}
	}
}
