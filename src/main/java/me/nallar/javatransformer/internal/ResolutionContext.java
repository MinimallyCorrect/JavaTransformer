package me.nallar.javatransformer.internal;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.TypeParameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import lombok.Data;
import lombok.NonNull;
import lombok.val;
import me.nallar.javatransformer.api.Type;

import java.util.*;

public class ResolutionContext {
	private final String packageName;
	private final Iterable<ImportDeclaration> imports;
	private final Iterable<TypeParameter> typeParameters;

	public ResolutionContext(String packageName, Iterable<ImportDeclaration> imports, Iterable<TypeParameter> typeParameters) {
		this.imports = imports;
		this.typeParameters = typeParameters;
		this.packageName = packageName;
	}

	public Type resolve(com.github.javaparser.ast.type.Type type) {
		return resolve(type.toStringWithoutComments().trim());
	}

	/**
	 * Resolves a given part to a JVM type string.
	 * <p>
	 * EG:
	 * ArrayList -> Ljava/util/ArrayList;
	 * T -> TT;
	 * boolean -> Z
	 *
	 * @param name
	 * @return JVM format resolved name
	 */
	public Type resolve(String name) {
		if (name == null)
			return null;

		String real = extractReal(name);
		Type type = resolveReal(real);

		String generic = extractGeneric(name);
		Type genericType = resolve(generic);

		if (generic == null) {
			if (type != null) {
				return type;
			}
		} else {
			if (type != null && genericType != null) {
				return addTypeArgument(type, genericType);
			}
		}
		throw new RuntimeException("Couldn't resolve name: " + name + "\nFound real type: " + type + "\nGeneric type: " + genericType);
	}

	private Type addTypeArgument(Type type, Type genericType) {
		throw new UnsupportedOperationException("TODO"); // TODO: 23/01/2016
	}

	private Type resolveReal(String name) {
		Type result = resolveTypeParameterType(name);
		if (result != null)
			return result;

		result = resolveImportedType(name);
		if (result != null)
			return result;

		result = resolveFullType(name);
		if (result != null)
			return result;

		return null;
	}

	private Type resolveFullType(String name) {
		if (!name.contains(".") && !Objects.equals(System.getProperty("JarTransformer.allowDefaultPackage"), "true")) {
			return null;
		}

		return new Type("L" + name + ";");
	}

	private Type resolveImportedType(String name) {
		String dotName = name.contains(".") ? name : '.' + name;

		for (ImportDeclaration anImport : imports) {
			String importName = anImport.getName().getName();
			if (importName.endsWith(dotName)) {
				return new Type("L" + importName + ";", null);
			}
		}

		return null;
	}

	/**
	 * If we have the type parameter "A extends StringBuilder",
	 * then "A" is resolved to a type with:
	 * descriptor: Ljava/lang/StringBuilder;
	 * signature: TA;
	 */
	private Type resolveTypeParameterType(String name) {
		for (TypeParameter typeParameter : typeParameters) {
			String typeName = typeParameter.getName();
			if (typeName.equals(name)) {
				val bounds = typeParameter.getTypeBound();
				String extends_ = "Ljava/lang/Object;";

				if (bounds != null) {
					if (bounds.size() == 1) {
						ClassOrInterfaceType scope = bounds.get(0).getScope();
						if (scope != null) {
							extends_ = resolve(scope.getName()).descriptor;
						}
					} else {
						throw new RuntimeException("Bounds must have one object, found: " + bounds);
					}
				}

				return new Type("L" + extends_, "T" + typeName + ";");
			}
		}

		return null;
	}

	private String extractGeneric(String name) {
		int bracket = name.indexOf('<');
		if (name.lastIndexOf('>') != name.length() - 1)
			throw new RuntimeException("Mismatched angled brackets in: " + name);
		return bracket == -1 ? null : name.substring(bracket + 1, name.length() - 1);
	}

	private String extractReal(String name) {
		int bracket = name.indexOf('<');
		return bracket == -1 ? name : name.substring(0, bracket);
	}

	public String unresolve(Type t) {
		if (t.isPrimitiveType()) {
			return t.getPrimitiveTypeName();
		}
		String className = t.getClassName();

		for (ImportDeclaration anImport : imports) {
			String importName = anImport.getName().getName();
			if (className.startsWith(importName)) {
				return className.replace(importName + ".", "");
			}
		}

		return className;
	}

	@Data
	private class DescriptorSignaturePair {
		@NonNull
		private final String descriptor;
		private final String signature;
	}
}
