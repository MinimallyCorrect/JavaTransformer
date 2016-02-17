package me.nallar.javatransformer.api;

import lombok.Data;
import lombok.NonNull;
import lombok.val;
import me.nallar.javatransformer.internal.ResolutionContext;
import me.nallar.javatransformer.internal.util.JVMUtil;

import java.util.*;

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

	/**
	 * A descriptor represents the real part of a type
	 */
	@NonNull
	public final String descriptor;
	/**
	 * A signature represents the generic part of a type
	 */
	public final String signature;

	public Type(String descriptor, String signature) {
		if (descriptor.isEmpty())
			throw new IllegalArgumentException("descriptor");

		if (signature != null && (signature.isEmpty() || signature.equals(descriptor)))
			signature = null;

		checkSignature(signature);

		this.descriptor = descriptor;
		this.signature = signature;
	}

	public Type(String descriptor) {
		this(descriptor, null);
	}

	private static void checkSignature(String signature) {
		int lastBracket = signature.lastIndexOf('>');
		if (lastBracket == -1)
			return;

		val after = signature.charAt(lastBracket + 1);
		if (after != ';' && after != '>')
			throw new TransformationException("Invalid signature. After generic brack should either be '>' or ';'");
	}

	public static Type of(String fullClassName) {
		// TODO: 23/01/2016 Handle inner classes properly? currently depend on following naming standards
		// depends on: lower case package names, uppercase first letter of class name
		String realType = ResolutionContext.extractReal(fullClassName);
		val type = new Type('L' + JVMUtil.classNameToJLSName(realType) + ';');

		String genericType = ResolutionContext.extractGeneric(fullClassName);
		if (genericType == null)
			return type;

		return type.withTypeArgument(Type.of(genericType));
	}

	public static List<Type> listOf(String desc, String signature) {
		val parsedDesc = splitTypes(desc);
		val parsedSignature = splitTypes(signature);

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

	public static List<String> splitTypes(final String signature) {
		if (signature == null)
			return null;

		val types = new ArrayList<String>();
		int pos = 0;
		char c;
		String current = "";
		String name;

		while (pos < signature.length())
			switch (c = signature.charAt(pos++)) {
				case 'Z':
				case 'C':
				case 'B':
				case 'S':
				case 'I':
				case 'F':
				case 'J':
				case 'D':
				case 'V':
					types.add(current + c + "");
					current = "";
					break;

				case '[':
					current += '[';
					break;

				case 'T':
					int end = signature.indexOf(';', pos);
					name = signature.substring(pos, end);
					pos = end + 1;
					types.add(current + 'T' + name + ';');
					current = "";
					break;
				case 'L':
					int start = pos;
					int genericCount = 0;
					innerLoop:
					while (pos < signature.length())
						switch (signature.charAt(pos++)) {
							case ';':
								if (genericCount > 0)
									break;
								name = signature.substring(start, pos - 1);
								types.add(current + 'L' + name + ';');
								current = "";
								break innerLoop;
							case '<':
								genericCount++;
								break;
							case '>':
								genericCount--;
								break;
						}
					break;
				default:
					throw new TransformationException("Unexpected character '" + c + "' in signature: " + signature);
			}
		return types;
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
			return getClassName();

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
		if (!isTypeParameter())
			throw new UnsupportedOperationException("Can't get type parameter name for type: " + this);
		return signature.substring(1, signature.length() - 1);
	}

	public Type getTypeArgument() {
		val start = signature.indexOf('<');
		val end = signature.lastIndexOf('>');
		return new Type(signature.substring(start + 1, end));
	}

	public String signatureIfExists() {
		return signature == null ? descriptor : signature;
	}

	public Type withTypeArgument(Type genericType) {
		if (this.isPrimitiveType())
			throw new UnsupportedOperationException("Can not add type argument to primitive type");

		String signature = signatureIfExists();
		int semicolon = signature.lastIndexOf(';');
		if (semicolon == -1)
			throw new IllegalStateException("Couldn't find ';' in: " + this);

		StringBuilder sb = new StringBuilder(signature);
		sb.insert(semicolon - 1, '<' + genericType.signatureIfExists() + '>');
		return new Type(descriptor, sb.toString());
	}

	public boolean similar(@NonNull Type other) {
		return UNKNOWN == this || UNKNOWN == other || this.equals(other);
	}
}
