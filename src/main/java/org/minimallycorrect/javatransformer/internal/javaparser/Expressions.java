package org.minimallycorrect.javatransformer.internal.javaparser;

import java.lang.reflect.Array;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithScope;

import org.minimallycorrect.javatransformer.api.TransformationException;
import org.minimallycorrect.javatransformer.api.Type;
import org.minimallycorrect.javatransformer.api.code.IntermediateValue;
import org.minimallycorrect.javatransformer.internal.ResolutionContext;

@UtilityClass
public class Expressions {
	@Contract(value = "null, _, _ -> fail; _, null, _ -> fail; _, _, true -> !null", pure = true)
	@NonNull
	@SuppressWarnings({"Contract", "unchecked"}) // Not violated, @NonNull adds null checks
	public static Type expressionToType(@NonNull Expression e, @NonNull ResolutionContext context, boolean failIfUnknown) {
		if (e instanceof StringLiteralExpr) {
			return Type.of(String.class);
		} else if (e instanceof BooleanLiteralExpr) {
			return Type.BOOLEAN;
		} else if (e instanceof ClassExpr) {
			// TODO: Class<T>
			return Type.of(Class.class);
		} else if (e instanceof NameExpr) {
			// TODO locals and fields of current class
			val ne = ((NameExpr) e).getName();
			//ne.
		} else if (e instanceof ArrayInitializerExpr) {
			// TODO
		} else if (e instanceof FieldAccessExpr) {
			val scope = ((FieldAccessExpr) e).getScope();
			Type scopeType = expressionToType(scope, context, false);
			if (scopeType == Type.UNKNOWN) {
				scopeType = context.resolve(scope.toString());
			}

			val clazzInfo = context.getClassPath().getClassInfo(scopeType.getClassName());
			val fieldName = ((FieldAccessExpr) e).getNameAsString();

			// TODO: we assume compiling against a class with non duplicate field names
			// there might be dupes for valid bytecode if obfuscated or from non-java language?
			val field = clazzInfo.getFields().filter(it -> it.getName().equals(fieldName)).findFirst().orElse(null);

			return field.getType();
		}

		if (failIfUnknown) {
			if (e instanceof NodeWithScope) {
				System.err.println(((NodeWithScope) e).getScope().getClass());
			}
			throw new TransformationException("Unknown value {" + e + "}\nClass: " + e.getClass());
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
			Type t = context.resolve(fieldAccessExpr.getScope().toString());
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
}
