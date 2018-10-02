package org.minimallycorrect.javatransformer.internal.javaparser;

import java.lang.reflect.Array;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.Resolvable;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;

import org.minimallycorrect.javatransformer.api.TransformationException;
import org.minimallycorrect.javatransformer.api.Type;
import org.minimallycorrect.javatransformer.api.code.IntermediateValue;
import org.minimallycorrect.javatransformer.internal.ResolutionContext;

@UtilityClass
public class Expressions {
	@Contract(value = "null, _, _ -> fail; _, null, _ -> fail; _, _, true -> !null", pure = true)
	@Nullable
	@SuppressWarnings({"Contract", "unchecked"}) // Not violated, @NonNull adds null checks
	public static Type expressionToType(@NonNull Expression e, @NonNull ResolutionContext context, boolean failIfUnknown) {
		if (e instanceof StringLiteralExpr) {
			return Type.of(String.class);
		} else if (e instanceof BooleanLiteralExpr) {
			return Type.BOOLEAN;
		} else if (e instanceof ClassExpr) {
			return Type.of(Class.class);
		} else if (e instanceof ArrayInitializerExpr) {
			// TODO
		} else if (e instanceof FieldAccessExpr) {
			/*
			// needs to be a constant -> must be a field which is an enum constant or
			// a public static final field of constable type
			val fieldAccessExpre = (FieldAccessExpr) e;
			Type t = context.resolve(Objects.requireNonNull(fieldAccessExpre.getScope().toString()));
			if (t != null)
				return t;
				*/
		}

		if (e instanceof Resolvable) {
			return AsmResolvedTypes.convertResolvedTypeToType(((Resolvable<ResolvedValueDeclaration>) e).resolve().getType());
		}

		if (failIfUnknown)
			throw new TransformationException("Unknown value: " + e + "\nClass: " + e.getClass());
		//noinspection Contract - Not violated, @NonNull adds null checks
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
			if (t != null)
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
