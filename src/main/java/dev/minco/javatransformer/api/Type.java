package dev.minco.javatransformer.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;

import org.jetbrains.annotations.Nullable;

import dev.minco.javatransformer.internal.ResolutionContext;
import dev.minco.javatransformer.internal.util.CollectionUtil;
import dev.minco.javatransformer.internal.util.JVMUtil;
import dev.minco.javatransformer.internal.util.Joiner;
import dev.minco.javatransformer.internal.util.Splitter;
import dev.minco.javatransformer.internal.util.TypeUtil;

/**
 * <pre>
 * {@code
 * 		a variable of type List<String> has:
 * 			real type: Ljava/util/List;
 * 			generic type: Ljava/util/List<Ljava/lang/String;>
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
 * }
 * </pre>
 */
@Data
public class Type {
	public static final Type UNKNOWN = new Type("Ljava/lang/Object;", "Tunknown;");
	public static final Type WILDCARD = new Type("Ljava/lang/Object;", "*");
	public static final Type OBJECT = Type.of("java.lang.Object");
	public static final Type BOOLEAN = new Type("Z");
	public static final Type DOUBLE = new Type("D");
	public static final Type FLOAT = new Type("F");
	public static final Type INT = new Type("I");
	public static final Type LONG = new Type("L");
	public static final Type CHAR = new Type("C");
	public static final Type STRING = Type.of("java.lang.String");
	public static final Type STATIC_META_CLASS = new Type("Ljvm/STATIC;", "Ljvm/STATIC;");
	public static final Type ANNOTATION = Type.of("java.lang.annotation.Annotation");
	public static final Type CLAZZ = Type.of("java.lang.Class");
	public static final Type LAMBDA = Type.of("Ljvm/LAMBDA;");

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

	private static void checkSignature(@Nullable String signature) {
		if (signature == null)
			return;

		int lastBracket = signature.lastIndexOf('>');
		if (lastBracket == -1)
			return;

		val after = signature.charAt(lastBracket + 1);
		if (after != ';' && after != '>')
			throw new TransformationException("Invalid signature '" + signature + "'. After generic bracket should either be '>' or ';'");
	}

	public static Type staticMetaclassOf(Type type) {
		return STATIC_META_CLASS.withTypeArgument(type);
	}

	public static Type of(String fullClassName) {
		// TODO: 23/01/2016 Handle inner classes properly? currently depend on following naming standards
		// depends on: lower case package names, uppercase first letter of class name
		String realType = ResolutionContext.extractReal(fullClassName);
		val type = new Type('L' + JVMUtil.classNameToJLSName(realType) + ';');

		String genericType = ResolutionContext.extractGeneric(fullClassName);
		if (genericType == null)
			return type;

		// TODO: check this handles Map<Map<a, b>, Map<b, d>> correctly?
		return type.withTypeArguments(CollectionUtil.stream(Splitter.commaSplitter.splitIterable(genericType)).map(Type::of).collect(Collectors.toList()));
	}

	public static Type of(Class<?> clazz) {
		return of(clazz.getName());
	}

	public static List<Type> listOf(String desc, @Nullable String signature) {
		val parsedDesc = TypeUtil.splitTypes(desc, false);
		val parsedSignature = TypeUtil.splitTypes(signature, true);

		if (parsedSignature != null && !parsedSignature.isEmpty() && parsedSignature.size() != parsedDesc.size()) {
			throw new TransformationException("Failed to parse type lists due to size mismatch." +
				"\n\tdesc: " + desc +
				"\n\tsignature: " + signature +
				"\n\tparsedDesc: " + parsedDesc +
				"\n\tparsedSignature: " + parsedSignature);
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
		return getDescriptorType().primitiveName != null;
	}

	public boolean isClassType() {
		return getDescriptorType() == DescriptorType.CLASS;
	}

	public boolean isArrayType() {
		return getDescriptorType() == DescriptorType.ARRAY;
	}

	public DescriptorType getDescriptorType() {
		return DescriptorType.of(descriptor.charAt(0));
	}

	public boolean isTypeParameter() {
		val signature = this.signature;

		return signature != null && signature.charAt(0) == 'T';
	}

	public String getJavaName() {
		if (isTypeParameter())
			return getTypeParameterName();
		val type = getDescriptorType();
		if (type.primitiveName != null)
			return type.primitiveName;
		else if (type == DescriptorType.CLASS)
			return getClassName() + (hasTypeArguments() ? "<" + Joiner.on(",").join(getTypeArguments().stream().map(Type::getJavaName)) + ">" : "");
		else if (type == DescriptorType.ARRAY)
			return getArrayContainedType().getJavaName() + "[]";

		throw new IllegalStateException("Unhandled descriptor type for type: " + this);
	}

	public String getPrimitiveTypeName() {
		val type = getDescriptorType();
		if (type.primitiveName == null)
			throw new UnsupportedOperationException("Can't get primitive type for: " + this);
		return type.primitiveName;
	}

	public String getClassName() {
		val type = getDescriptorType();
		if (type != DescriptorType.CLASS)
			throw new UnsupportedOperationException("Can't get class name for: " + this);
		return descriptorWithoutArray().substring(1, descriptor.length() - 1).replace('/', '.');
	}

	public String getTypeParameterName() {
		if (signature == null || !isTypeParameter())
			throw new UnsupportedOperationException("Can't get type parameter name for type: " + this);
		return signature.substring(1, signature.length() - 1);
	}

	public Type remapClassNames(Function<String, String> mapper) {
		val type = getDescriptorType();
		if (type == DescriptorType.ARRAY)
			return getArrayContainedType().remapClassNames(mapper).withArrayCount(1);

		if (type != DescriptorType.CLASS)
			return new Type(descriptor, signature);

		Type mappedType = Type.of(mapper.apply(getClassName()));
		if (isTypeParameter())
			mappedType = new Type(mappedType.descriptor, signature);

		if (hasTypeArguments())
			mappedType = mappedType.withTypeArguments(getTypeArguments().stream().map(it -> it.remapClassNames(mapper)).collect(Collectors.toList()));

		return mappedType;
	}

	public Type getArrayContainedType() {
		val type = getDescriptorType();
		if (type != DescriptorType.ARRAY)
			throw new UnsupportedOperationException("Can't get array contained type for: " + this);
		return new Type(descriptor.substring(1), signatureElseDescriptor().substring(1));
	}

	public boolean hasTypeArguments() {
		return signature != null && signature.indexOf('<') != -1;
	}

	public List<Type> getTypeArguments() {
		val arguments = ResolutionContext.extractGeneric(signature);
		if (arguments == null)
			throw new UnsupportedOperationException("Can't get type argument for type: " + this);

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

	public String descriptorWithoutArray() {
		return descriptor.substring(descriptor.lastIndexOf('[') + 1);
	}

	public Type withTypeArgument(Type of) {
		return withTypeArguments(Collections.singletonList(of));
	}

	public Type withTypeArguments(Iterable<Type> genericType) {
		if (getDescriptorType().primitiveName != null)
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
		String simpleName;

		try {
			simpleName = getJavaName();
		} catch (Exception e) {
			simpleName = e.toString();
		}

		return "Type(descriptor=" + this.descriptor + ", signature=" + this.signature + ", simpleName=" + simpleName + ")";
	}

	public Type withArrayCount(int arrayCount) {
		if (arrayCount == 0)
			return this;

		val brackets = new char[arrayCount];
		Arrays.fill(brackets, '[');
		return new Type(new String(brackets) + descriptor, signature);
	}

	public Type withClassName(String name) {
		val descriptor = "L" + name.replace('.', '/') + ";";
		String signature = this.signature;

		if (signature != null)
			signature = descriptor + signature.substring(signature.indexOf(';') + 1);

		return new Type(descriptor, signature);
	}

	public static boolean isAssignableFrom(Type to, Type from) {
		return to.descriptor.equals(from.descriptor) ||
			(to.isClassType() && to.getClassName().equals("java.lang.Object") && (from.isArrayType() || from.isClassType()));
	}

	public boolean isStaticMetaClass() {
		return descriptor == STATIC_META_CLASS.descriptor;
	}

	@AllArgsConstructor
	public enum DescriptorType {
		BYTE("byte"),
		CHAR("char"),
		DOUBLE("double"),
		FLOAT("float"),
		INT("int"),
		LONG("long"),
		SHORT("short"),
		VOID("void"),
		BOOLEAN("boolean"),
		ARRAY(null),
		CLASS(null),
		// Project Valhalla, currently unused
		VALUE(null),
		UNION(null);

		@Getter
		final String primitiveName;

		public static DescriptorType of(char c) {
			switch (c) {
				case '[':
					return ARRAY;
				case 'L':
					return CLASS;
				case 'Q':
					return VALUE;
				case 'U':
					return UNION;
				case 'B':
					return BYTE;
				case 'C':
					return CHAR;
				case 'D':
					return DOUBLE;
				case 'F':
					return FLOAT;
				case 'I':
					return INT;
				case 'J':
					return LONG;
				case 'S':
					return SHORT;
				case 'V':
					return VOID;
				case 'Z':
					return BOOLEAN;
			}
			throw new UnknownDescriptorTypeException("Unknnown descriptor type '" + c + "'");
		}

		@Nullable
		public Integer getByteWidth() {
			switch (this) {
				case BYTE:
					return 1;
				case CHAR:
				case SHORT:
					return 2;
				case INT:
					return 4;
				case LONG:
					return 8;
			}
			return null;
		}

		private static class UnknownDescriptorTypeException extends RuntimeException {
			private static final long serialVersionUID = 0;

			public UnknownDescriptorTypeException(String s) {
				super(s);
			}
		}
	}
}
