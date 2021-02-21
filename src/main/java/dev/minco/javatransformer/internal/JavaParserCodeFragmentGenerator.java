package dev.minco.javatransformer.internal;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithBlockStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import dev.minco.javatransformer.api.MethodInfo;
import dev.minco.javatransformer.api.TransformationException;
import dev.minco.javatransformer.api.Type;
import dev.minco.javatransformer.api.code.CodeFragment;
import dev.minco.javatransformer.api.code.IntermediateValue;
import dev.minco.javatransformer.internal.javaparser.Expressions;
import dev.minco.javatransformer.internal.util.CachingSupplier;
import dev.minco.javatransformer.internal.util.CodeFragmentUtil;
import dev.minco.javatransformer.internal.util.NodeUtil;

public class JavaParserCodeFragmentGenerator {
	static Class<?> concreteImplementation(Class<?> interfaceType) {
		if (interfaceType == CodeFragment.Body.class)
			return CallableDeclarationCodeFragment.class;
		if (interfaceType == CodeFragment.MethodCall.class)
			return MethodCall.class;
		throw new UnsupportedOperationException("No ASM implementation for " + interfaceType);
	}

	public abstract static class JavaParserCodeFragment implements CodeFragment {
		final SourceInfo.CallableDeclarationWrapper<?> containingWrapper;

		protected Node getNode() {
			return getContainingBody();
		}

		public JavaParserCodeFragment(SourceInfo.CallableDeclarationWrapper<?> containingWrapper) {
			this.containingWrapper = containingWrapper;
		}

		@Override
		public ExecutionOutcome getExecutionOutcome() {
			// TODO: implement this?
			return new ExecutionOutcome(true, true, true);
		}

		@Override
		public void insert(@NonNull CodeFragment codeFragment, @NonNull InsertionPosition position, @NonNull InsertionOptions insertionOptions) {
			if (CodeFragmentUtil.validateInsert(this, codeFragment, position, insertionOptions)) {
				return;
			}

			if (!(codeFragment instanceof JavaParserCodeFragment)) {
				throw new UnsupportedOperationException("Can't insert source code into byte code");
			}

			val cf = (JavaParserCodeFragment) codeFragment;
			Node currentNode = getNode();
			Node insertNode = cf.getNode().clone();

			SourceInfo.changeTypeContext(cf.containingWrapper.getContext(), containingWrapper.getContext(), insertNode);

			if (insertNode instanceof BlockStmt) {
				val statements = ((BlockStmt) insertNode).getStatements();
				if (statements.size() == 0 && (position == InsertionPosition.BEFORE || position == InsertionPosition.AFTER)) {
					return;
				}
				if (statements.size() == 1) {
					insertNode = statements.get(0);
				}
			}

			if (!(insertNode instanceof Statement)) {
				if (insertNode instanceof Expression) {
					insertNode = new ExpressionStmt((Expression) insertNode);
				} else {
					throw new UnsupportedOperationException("TODO " + insertNode.getClass() + "\n" + insertNode);
				}
			}

			if (currentNode instanceof NodeWithBlockStmt) {
				currentNode = ((NodeWithBlockStmt) currentNode).getBody();
			}

			switch (position) {
				case BEFORE:
					((BlockStmt) currentNode).addStatement(0, (Statement) insertNode);
					break;
				case OVERWRITE:
					throw new UnsupportedOperationException();
				case AFTER:
					((BlockStmt) currentNode).addStatement((Statement) insertNode);
					break;
			}
		}

		private BlockStmt getContainingBody() {
			return Objects.requireNonNull(containingWrapper.getBody());
		}

		@Override
		@SuppressWarnings({"JavaReflectionMemberAccess", "unchecked"})
		@SneakyThrows
		public <T extends CodeFragment> List<T> findFragments(Class<T> fragmentType) {
			if (fragmentType.isInstance(this))
				return Collections.singletonList((T) this);

			val constructor = (Constructor<T>) concreteImplementation(fragmentType).getDeclaredConstructors()[0];
			val list = new ArrayList<T>();
			for (Node node : NodeUtil.findWithinMethodScope((Class<? extends Node>) constructor.getParameterTypes()[1], getContainingBody())) {
				list.add(constructor.newInstance(containingWrapper, node));
			}

			return list;
		}
	}

	public static class CallableDeclarationCodeFragment extends JavaParserCodeFragment implements CodeFragment.Body {
		public CallableDeclarationCodeFragment(SourceInfo.CallableDeclarationWrapper<?> containingWrapper) {
			super(containingWrapper);
		}

		@NonNull
		@Override
		public List<IntermediateValue> getInputTypes() {
			return containingWrapper.getParameters().stream().map(it -> new IntermediateValue(it.type, IntermediateValue.UNKNOWN, new IntermediateValue.Location(IntermediateValue.LocationType.LOCAL, -1, it.name))).collect(Collectors.toList());
		}

		@NonNull
		@Override
		public List<IntermediateValue> getOutputTypes() {
			val ret = containingWrapper.getReturnType();
			if (ret.getDescriptorType() == Type.DescriptorType.VOID)
				return Collections.emptyList();

			return Collections.singletonList(new IntermediateValue(ret, IntermediateValue.UNKNOWN, new IntermediateValue.Location(IntermediateValue.LocationType.LOCAL, -1, "return;")));
		}
	}

	public static class MethodCall extends JavaParserCodeFragment implements CodeFragment.MethodCall {
		final MethodCallExpr expr;
		final CachingSupplier<List<Type>> inputTypes;
		final CachingSupplier<MethodInfo> methodInfo;

		public MethodCall(SourceInfo.CallableDeclarationWrapper<?> containingWrapper, MethodCallExpr methodCallExpr) {
			super(containingWrapper);
			this.expr = methodCallExpr;
			inputTypes = CachingSupplier.of(() -> Expressions.getMethodCallInputTypes(expr, containingWrapper.getContext()));
			methodInfo = CachingSupplier.of(() -> {
				val context = containingWrapper.getContext();
				val name = getName();
				val scopeType = Expressions.expressionToType(expr.getScope().orElse(new ThisExpr()), context, true);
				val mi = context.resolveMethodCallType(scopeType, name, inputTypes);
				if (mi == null) {
					throw new TransformationException("Couldn't find method {" + name + "} on {" + scopeType + "} for {" + expr + "} with params {" + Expressions.getMethodCallInputTypes(expr, context) + "}");
				}
				return mi;
			});
		}

		@Override
		protected Node getNode() {
			return expr;
		}

		@NonNull
		@Override
		public List<IntermediateValue> getInputTypes() {
			return Expressions.getMethodCallInputIVs(expr, containingWrapper.getContext(), inputTypes, methodInfo.get());
		}

		@NonNull
		@Override
		public List<IntermediateValue> getOutputTypes() {
			return Collections.emptyList();
		}

		@NonNull
		@Override
		public Type getContainingClassType() {
			return methodInfo.get().getClassInfo().getType();
		}

		@NonNull
		@Override
		public String getName() {
			return expr.getNameAsString();
		}
	}
}
