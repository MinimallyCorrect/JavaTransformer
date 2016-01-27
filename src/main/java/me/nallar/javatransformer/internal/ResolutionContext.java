package me.nallar.javatransformer.internal;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.TypeParameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import lombok.NonNull;
import lombok.val;
import me.nallar.javatransformer.api.Type;
import me.nallar.javatransformer.internal.util.JVMUtil;
import me.nallar.javatransformer.internal.util.NodeUtil;

import java.util.*;

public class ResolutionContext {
	@NonNull
	private final String packageName;
	@NonNull
	private final Iterable<ImportDeclaration> imports;
	@NonNull
	private final Iterable<TypeParameter> typeParameters;

	private ResolutionContext(String packageName, Iterable<ImportDeclaration> imports, Iterable<TypeParameter> typeParameters) {
		this.packageName = packageName;
		this.imports = imports;
		this.typeParameters = typeParameters;
	}

	public static ResolutionContext of(String packageName, Iterable<ImportDeclaration> imports, Iterable<TypeParameter> typeParameters) {
		return new ResolutionContext(packageName, imports, typeParameters);
	}

	public static ResolutionContext of(Node node) {
		CompilationUnit cu = NodeUtil.getParentNode(node, CompilationUnit.class);
		String packageName = NodeUtil.qualifiedName(cu.getPackage().getName());
		List<TypeParameter> typeParameters = NodeUtil.getTypeParameters(node);

		return new ResolutionContext(packageName, cu.getImports(), typeParameters);
	}

	static boolean hasPackages(String name) {
		// Guesses whether input name includes packages or is just classes
		return Character.isUpperCase(name.charAt(0)) && name.indexOf('.') != -1;
	}

	static String classNameToDescriptor(String className) {
		// TODO: 23/01/2016 Handle inner classes properly? currently depend on following naming standards
		// depends on: lower case package names, uppercase first letter of class name
		return 'L' + JVMUtil.classNameToJLSName(className) + ';';
	}

	static String extractGeneric(String name) {
		int leftBracket = name.indexOf('<');
		int rightBracket = name.indexOf('>');

		if (leftBracket == -1 && rightBracket == -1)
			return null;

		if (rightBracket == name.length() - 1 && leftBracket != -1 && leftBracket < rightBracket)
			return name.substring(leftBracket + 1, rightBracket);

		throw new RuntimeException("Mismatched angled brackets in: " + name);
	}

	static String extractReal(String name) {
		int bracket = name.indexOf('<');
		return bracket == -1 ? name : name.substring(0, bracket);
	}

	public Type resolve(com.github.javaparser.ast.type.Type type) {
		if (type instanceof ClassOrInterfaceType) {
			return resolve(((ClassOrInterfaceType) type).getName());
		} else if (type instanceof PrimitiveType) {
			return new Type(JVMUtil.primitiveTypeToDescriptor(((PrimitiveType) type).getType().name().toLowerCase()));
		} else {
			// TODO: 23/01/2016 Is this behaviour correct?
			return resolve(type.toStringWithoutComments());
		}
	}

	/**
	 * Resolves a given name to a JVM type string.
	 * <p>
	 * EG:
	 * ArrayList -> Ljava/util/ArrayList;
	 * T -> TT;
	 * boolean -> Z
	 *
	 * @param name Name to resolve
	 * @return Type containing resolved name with descriptor/signature
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
				return sanityCheck(type);
			}
		} else {
			if (type != null && genericType != null) {
				return sanityCheck(type.withTypeArgument(genericType));
			}
		}

		throw new RuntimeException("Couldn't resolve name: " + name + "\nFound real type: " + type + "\nGeneric type: " + genericType);
	}

	private Type sanityCheck(Type type) {
		if (type.isClassType() && (type.getClassName().endsWith(".")) || !type.getClassName().contains(".")) {
			throw new RuntimeException("Unexpected class name (incorrect dots) in type: " + type);
		}

		return type;
	}

	private Type resolveReal(String name) {
		Type result = resolveTypeParameterType(name);
		if (result != null)
			return result;

		result = resolveClassType(name);
		if (result != null)
			return result;

		return null;
	}

	private Type resolveClassType(String name) {
		String dotName = name.contains(".") ? name : '.' + name;

		for (ImportDeclaration anImport : imports) {
			String importName = NodeUtil.qualifiedName(anImport.getName());
			if (importName.endsWith(dotName)) {
				return new Type("L" + importName + ";", null);
			}
		}

		Type type = resolveIfExists(packageName + '.' + name);
		if (type != null) {
			return type;
		}

		for (ImportDeclaration anImport : imports) {
			String importName = NodeUtil.qualifiedName(anImport.getName());
			if (importName.endsWith(".*")) {
				type = resolveIfExists(importName.replace(".*", ".") + name);
				if (type != null) {
					return type;
				}
			}
		}

		type = resolveIfExists("java.lang." + name);
		if (type != null) {
			return type;
		}

		if (!hasPackages(name) && !Objects.equals(System.getProperty("JarTransformer.allowDefaultPackage"), "true")) {
			return null;
		}

		return new Type(classNameToDescriptor(name));
	}

	private Type resolveIfExists(String s) {
		if (s.startsWith("java.") || s.startsWith("javax.")) {
			try {
				return new Type(Class.forName(s).getName());
			} catch (ClassNotFoundException ignored) {
			}
		}
		// TODO: 23/01/2016 Move to separate class, do actual searching for files
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

	public String unresolve(Type t) {
		if (t.isTypeParameter()) {
			return t.getTypeParameterName();
		}
		if (t.isPrimitiveType()) {
			return t.getPrimitiveTypeName();
		}
		String className = t.getClassName();

		for (ImportDeclaration anImport : imports) {
			String importName = NodeUtil.qualifiedName(anImport.getName());
			if (className.startsWith(importName)) {
				return className.replace(importName + ".", "");
			}
		}

		return className;
	}
}
