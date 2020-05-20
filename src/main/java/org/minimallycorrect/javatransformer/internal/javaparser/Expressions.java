package org.minimallycorrect.javatransformer.internal.javaparser;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.val;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithScope;

import org.minimallycorrect.javatransformer.api.AccessFlags;
import org.minimallycorrect.javatransformer.api.MethodInfo;
import org.minimallycorrect.javatransformer.api.TransformationException;
import org.minimallycorrect.javatransformer.api.Type;
import org.minimallycorrect.javatransformer.api.code.IntermediateValue;
import org.minimallycorrect.javatransformer.internal.ResolutionContext;
import org.minimallycorrect.javatransformer.internal.util.CachingSupplier;

public final class Expressions {
	@Contract(value = "null, _, _ -> fail; _, null, _ -> fail; _, _, true -> !null", pure = true)
	@NonNull
	@SuppressWarnings({"Contract", "unchecked"}) // Not violated, @NonNull adds null checks
	public static Type expressionToType(@NonNull Expression e, @NonNull ResolutionContext context, boolean failIfUnknown) {
		if (e instanceof EnclosedExpr) {
			e = ((EnclosedExpr) e).getInner();
		}

		if (e instanceof CastExpr) {
			return context.resolve(((CastExpr) e).getType());
		}
		if (e instanceof StringLiteralExpr) {
			return Type.STRING;
		}
		if (e instanceof CharLiteralExpr) {
			return Type.CHAR;
		} else if (e instanceof BooleanLiteralExpr) {
			return Type.BOOLEAN;
		} else if (e instanceof IntegerLiteralExpr) {
			return Type.INT;
		} else if (e instanceof LongLiteralExpr) {
			return Type.LONG;
		} else if (e instanceof ClassExpr) {
			val ce = (ClassExpr) e;
			return Type.CLAZZ.withTypeArgument(context.resolve(ce.getType()));
		} else if (e instanceof ObjectCreationExpr) {
			val oce = (ObjectCreationExpr) e;
			// TODO: <T>
			return context.resolve(oce.getType());
		} else if (e instanceof BinaryExpr) {
			return expressionToType(((BinaryExpr) e).getLeft(), context, false);
		} else if (e instanceof NameExpr || e instanceof ThisExpr || e instanceof SuperExpr) {
			return context.resolveNameInExpressionContext(e.toString());
		} else if (e instanceof ArrayCreationExpr) {
			val ace = (ArrayCreationExpr) e;
			return context.resolve(ace.createdType());
		} else if (e instanceof FieldAccessExpr) {
			// NB some field accesses look like local var accesses and need handled separately
			// YAY INCONSISTENT SYNTAX!

			val scope = ((FieldAccessExpr) e).getScope();
			Type scopeType = expressionToType(scope, context, false);

			val type = context.resolveFieldType(scopeType, ((FieldAccessExpr) e).getNameAsString());
			if (type != Type.UNKNOWN) {
				return type;
			}
			return Type.STATIC_META_CLASS.withTypeArgument(context.resolve(e.toString()));
		} else if (e instanceof MethodCallExpr) {
			val mce = (MethodCallExpr) e;

			val scope = mce.getScope().orElse(new ThisExpr());
			val scopeType = expressionToType(scope, context, false);
			val inputUsedTypes = CachingSupplier.of(() -> getMethodCallInputTypes(mce, context));
			val method = context.resolveMethodCallType(scopeType, mce.getNameAsString(), inputUsedTypes);
			if (method != null) {
				val type = method.getReturnType();
				if (type != Type.UNKNOWN) {
					if (type.isTypeParameter()) {
						val params = method.getParameters();

						// TODO this isn't generic at all
						// needs to be able to recurse deeper
						if (scopeType.hasTypeArguments()) {
							val typeArgs = method.getClassInfo().getTypeVariables();
							val scopeTypeArgs = scopeType.getTypeArguments();
							for (int i = 0; i < typeArgs.size(); i++) {
								if (typeArgs.get(i).getName().equals(type.getTypeParameterName())) {
									return scopeTypeArgs.get(i);
								}
							}
						}

						for (int i = 0; i < params.size(); i++) {
							val pType = params.get(i).type;
							if (pType.isTypeParameter() && pType.getTypeParameterName().equals(type.getTypeParameterName())) {
								return inputUsedTypes.get().get(i);
							}
							if (!pType.hasTypeArguments()) {
								continue;
							}
							val tas = pType.getTypeArguments();
							for (int j = 0; j < tas.size(); j++) {
								val ta = tas.get(i);
								if (ta.isTypeParameter() && ta.getTypeParameterName().equals(type.getTypeParameterName())) {
									return inputUsedTypes.get().get(i).getTypeArguments().get(j);
								}
							}
						}
					}

					return type;
				}
			}
			System.err.println("Couldn't find method {" + mce.getNameAsString() + "} in {" + scopeType + "}");
		} else if (e instanceof LambdaExpr) {
			return Type.LAMBDA;
		}

		if (failIfUnknown) {
			if (e instanceof NodeWithScope) {
				System.err.println(((NodeWithScope) e).getScope().getClass());
			}
			throw new TransformationException("Unknown type for expression {" + e + "}\nClass: " + e.getClass());
		}

		return Type.UNKNOWN;
	}

	@Contract(value = "null, _, -> fail; _, null -> fail; _, _ -> !null", pure = true)
	@NonNull
	public static Object expressionToValue(@NonNull Expression e, @NonNull ResolutionContext context) {
		return expressionToValue(e, context, true);
	}

	@Contract(value = "null, _, _ -> fail; _, null, _ -> fail; _, _, true -> !null", pure = true)
	@Nullable
	@SuppressWarnings("Contract") // Not violated, @NonNull adds null checks
	public static Object expressionToValue(@NonNull Expression e, @NonNull ResolutionContext context, boolean failIfUnknown) {
		if (e instanceof StringLiteralExpr) {
			return ((StringLiteralExpr) e).getValue();
		} else if (e instanceof BooleanLiteralExpr) {
			return ((BooleanLiteralExpr) e).getValue();
		} else if (e instanceof ClassExpr) {
			return ((ClassExpr) e).getType().asString();
		} else if (e instanceof ArrayInitializerExpr) {
			return arrayExpressionToArray((ArrayInitializerExpr) e, context, failIfUnknown);
		} else if (e instanceof FieldAccessExpr) {
			// needs to be a constant -> must be a field which is an enum constant or
			// a public static final field of constable type
			// TODO: handle public static final fields properly? currently treated as enum
			val fieldAccessExpr = (FieldAccessExpr) e;
			Type t = expressionToType(fieldAccessExpr, context, false);
			return new String[]{t.getDescriptor(), ((FieldAccessExpr) e).getNameAsString()};
		} else if (e instanceof NullLiteralExpr) {
			return null;
		}
		if (failIfUnknown)
			throw new TransformationException("Unknown value: " + e + "\nClass: " + e.getClass());
		return IntermediateValue.UNKNOWN;
	}

	@Contract(value = "null, _ -> fail; _, null -> fail; _, _ -> !null", pure = true)
	@NonNull
	public static IntermediateValue expressionToIntermediateValue(@NonNull Expression e, @NonNull ResolutionContext r) {
		Type type = expressionToType(e, r, true);
		val value = expressionToValue(e, r, false);
		return new IntermediateValue(type, value, new IntermediateValue.Location(IntermediateValue.LocationType.STACK, -1, "argument"));
	}

	@Contract(pure = true)
	@NonNull
	private static Object[] arrayExpressionToArray(ArrayInitializerExpr expr, ResolutionContext context, boolean failIfUnknown) {
		val values = expr.getValues();
		if (values.isEmpty())
			return new Object[0];

		val results = values.stream().map(it -> expressionToValue(it, context, failIfUnknown)).collect(Collectors.toList());
		return results.toArray((Object[]) Array.newInstance(results.get(0).getClass(), 0));
	}

	@NonNull
	public static List<IntermediateValue> getMethodCallInputIVs(MethodCallExpr expr, ResolutionContext context, CachingSupplier<List<Type>> inputTypes, MethodInfo methodInfo) {
		val parameters = methodInfo.getParameters();

		val list = new ArrayList<IntermediateValue>();

		// this
		if (!methodInfo.getAccessFlags().has(AccessFlags.ACC_STATIC)) {
			list.add(new IntermediateValue(methodInfo.getClassInfo().getType(), IntermediateValue.UNKNOWN,
				new IntermediateValue.Location(IntermediateValue.LocationType.STACK, -1, "argument")));
		}

		val args = expr.getArguments();
		int maxArgs = parameters.size();
		boolean applyVarargs = false;
		if (methodInfo.getAccessFlags().has(AccessFlags.ACC_VARARGS)) {
			val its = inputTypes.get();
			if (its.size() == 0 || its.size() != maxArgs || !its.get(its.size() - 1).isArrayType()) {
				applyVarargs = true;
				maxArgs--;
			}
		}
		for (int i = 0; i < maxArgs; i++) {
			val it = args.get(i);
			list.add(new IntermediateValue(parameters.get(i).type, expressionToValue(it, context, false),
				new IntermediateValue.Location(IntermediateValue.LocationType.STACK, -1, "argument")));
		}
		if (applyVarargs) {
			// TODO: varargs values always unknown
			list.add(new IntermediateValue(parameters.get(maxArgs).type, IntermediateValue.UNKNOWN,
				new IntermediateValue.Location(IntermediateValue.LocationType.STACK, -1, "argument")));
		}

		return list;
	}

	@NonNull
	public static List<Type> getMethodCallInputTypes(MethodCallExpr expr, ResolutionContext context) {
		val list = new ArrayList<Type>();

		for (val it : expr.getArguments()) {
			list.add(expressionToType(it, context, false));
		}

		return list;
	}
}
