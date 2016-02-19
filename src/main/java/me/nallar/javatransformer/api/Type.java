package me.nallar.javatransformer.api;

import lombok.Data;
import lombok.NonNull;
import lombok.val;
import me.nallar.javatransformer.internal.ResolutionContext;
import me.nallar.javatransformer.internal.util.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * <pre><code>
 * 		a variable of type List<String> has:
 * 			real type: Ljava/util/List;
 * 			generic type: Ljava/util/List<Ljava/lang/String
 *
 * 		;>
 *
 *     When the type parameter T is <T:Ljava/lang/Object;>
 *
 * 		a variable of type T has:
 * 			real type: Ljava/lang/Object;
 * 			generic type: TT;
 *
 * 		a variable of type List<T> has:
 * 			real type: Ljava/util/List;
 * 			generic type: Ljava/util/List<TT;>
 * // access flags 0x8
 * // signature <T:Ljava/lang/Object;>(Ljava/util/ArrayList<TT;>;Ljava/util/List<Ljava/lang/String;>;)TT;
 * // declaration: T test<T>(java.util.ArrayList<T>, java.util.List<java.lang.String>)
 * static test(Ljava/util/ArrayList;Ljava/util/List;)Ljava/lang/Object;
 * </code></pre>
 */
@Data
public class Type {
	public static final Type UNKNOWN = new Type("Ljava/lang/Object;", "Tunknown;");
	public static final Type OBJECT = Type.of("java.lang.Object");

	/**
	 * A descriptor represents the real part of a type
	 */
	@NonNull
	public final String descriptor;
	/**
	 * A signature represents the generic part of a type
	 */
	@Nullable
	public final String signature;

	public Type(String descriptor, @Nullable String signature) {
		if (descriptor.isEmpty())
			throw new IllegalArgumentException("descriptor");

		if (signature != null && (signature.isEmpty() || signature.equals(descriptor)))
			signature = null;

		checkDescriptor(descriptor);
		checkSignature(signature);

		this.descriptor = descriptor;
		this.signature = signature;
	}

	public Type(String descriptor) {
		this(descriptor, null);
	}

	private static void checkDescriptor(String descriptor) {
		if (descriptor.charAt(0) == 'T')
			throw new TransformationException("Invalid descriptor '" + descriptor + "'");
	}

	private static void checkSignature(String signature) {
		if (signature == null)
			return;

		int lastBracket = signature.lastIndexOf('>');
		if (lastBracket == -1)
			return;

		val after = signature.charAt(lastBracket + 1);
		if (after != ';' && after != '>')
			throw new TransformationException("Invalid signature '" + signature + "'. After generic bracket should either be '>' or ';'");
	}

	public static Type of(String fullClassName) {
		// TODO: 23/01/2016 Handle inner classes properly? currently depend on following naming standards
		// depends on: lower case package names, uppercase first letter of class name
		String realType = ResolutionContext.extractReal(fullClassName);
		val type = new Type('L' + JVMUtil.classNameToJLSName(realType) + ';');

		String genericType = ResolutionContext.extractGeneric(fullClassName);
		if (genericType == null)
			return type;

		return type.withTypeArguments(CollectionUtil.stream(Splitter.commaSplitter.splitIterable(genericType)).map(Type::of).collect(Collectors.toList()));
	}

	public static List<Type> listOf(String desc, String signature) {
		val parsedDesc = TypeUtil.splitTypes(desc, false);
		val parsedSignature = TypeUtil.splitTypes(signature, true);

		if (parsedSignature != null && parsedSignature.size() != parsedDesc.size()) {
			throw new TransformationException("Failed to parse type lists." +
				"\n\tdesc: " + desc +
				"\n\tsignature: " + signature +
				"\n\tparsedDesc: " + parsedDesc +
				"\n\tparsedSignature: " + parsedSignature
			);
		}

		val types = new ArrayList<Type>();
		for (int i = 0; i < parsedDesc.size(); i++) {
			String real = parsedDesc.get(i);
			String generic = parsedSignature == null ? null : parsedSignature.get(i);
			types.add(new Type(real, generic));
		}

		return types;
	}

	public static Type ofSignature(String signature) {
		if (signature.charAt(0) == 'T')
			return new Type("Ljava/lang/Object;", signature);

		return new Type(ResolutionContext.extractReal(signature), signature);
	}

	public boolean isPrimitiveType() {
		val first = descriptor.charAt(0);
		return first != 'L' && first != 'T';
	}

	public boolean isClassType() {
		val first = descriptor.charAt(0);
		return first == 'L';
	}

	public boolean isTypeParameter() {
		val signature = this.signature;

		return signature != null && signature.charAt(0) == 'T';
	}

	public String getSimpleName() {
		if (isTypeParameter())
			return getTypeParameterName();
		else if (isPrimitiveType())
			return getPrimitiveTypeName();
		else if (isClassType())
			return getClassName() + (hasTypeArguments() ? "<" + Joiner.on(",").join(getTypeArguments().stream().map(Type::getSimpleName)) + ">" : "");

		throw new IllegalStateException("Unknown type for type: " + this);
	}

	public String getPrimitiveTypeName() {
		if (!isPrimitiveType())
			throw new UnsupportedOperationException("Can't get classname for type: " + this);
		return JVMUtil.descriptorToPrimitiveType(descriptor);
	}

	public String getClassName() {
		if (!isClassType())
			throw new UnsupportedOperationException("Can't get classname for type: " + this);
		return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
	}

	public String getTypeParameterName() {
		if (signature == null || !isTypeParameter())
			throw new UnsupportedOperationException("Can't get type parameter name for type: " + this);
		return signature.substring(1, signature.length() - 1);
	}

	public Type remapClassNames(Function<String, String> mapper) {
		if (!this.isClassType())
			return new Type(descriptor, signature);

		Type mappedType = Type.of(mapper.apply(getClassName()));
		if (isTypeParameter())
			mappedType = new Type(mappedType.descriptor, signature);

		if (hasTypeArguments())
			mappedType = mappedType.withTypeArguments(getTypeArguments().stream().map(it -> it.remapClassNames(mapper)).collect(Collectors.toList()));

		return mappedType;
	}

	public boolean hasTypeArguments() {
		return signature != null && signature.indexOf('<') != -1;
	}

	public List<Type> getTypeArguments() {
		if (signature == null || !hasTypeArguments())
			throw new UnsupportedOperationException("Can't get type argument for type: " + this);

		val arguments = ResolutionContext.extractGeneric(signature);
		val argumentList = new ArrayList<Type>();

		for (String argument : CollectionUtil.iterable(TypeUtil.readTypes(arguments, true))) {
			argumentList.add(Type.ofSignature(argument));
		}

		assert !argumentList.isEmpty();

		return argumentList;
	}

	public String signatureElseDescriptor() {
		return signature == null ? descriptor : signature;
	}

	public Type withTypeArgument(Type of) {
		return withTypeArguments(Collections.singletonList(of));
	}

	public Type withTypeArguments(Iterable<Type> genericType) {
		if (this.isPrimitiveType())
			throw new UnsupportedOperationException("Can not add type argument to primitive type");

		String signature = signatureElseDescriptor();
		int semicolon = signature.lastIndexOf(';');
		if (semicolon == -1)
			throw new IllegalStateException("Couldn't find ';' in: " + this);

		StringBuilder sb = new StringBuilder(signature);
		sb.insert(semicolon, '<' + Joiner.on().join(CollectionUtil.stream(genericType).map(Type::signatureElseDescriptor)) + '>');
		return new Type(descriptor, sb.toString());
	}

	public boolean similar(@NonNull Type other) {
		return UNKNOWN == this || UNKNOWN == other || this.descriptor.equals(other.descriptor);
	}

	public String toString() {
		return "Type(descriptor=" + this.descriptor + ", signature=" + this.signature + ", simpleName=" + getSimpleName() + ")";
	}
}
