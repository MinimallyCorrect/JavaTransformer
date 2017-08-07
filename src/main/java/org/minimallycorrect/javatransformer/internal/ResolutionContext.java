package org.minimallycorrect.javatransformer.internal;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.*;
import lombok.NonNull;
import lombok.val;
import org.minimallycorrect.javatransformer.api.TransformationException;
import org.minimallycorrect.javatransformer.api.Type;
import org.minimallycorrect.javatransformer.api.TypeVariable;
import org.minimallycorrect.javatransformer.internal.util.JVMUtil;
import org.minimallycorrect.javatransformer.internal.util.Joiner;
import org.minimallycorrect.javatransformer.internal.util.NodeUtil;
import org.minimallycorrect.javatransformer.internal.util.Splitter;

import java.util.*;
import java.util.stream.*;

public class ResolutionContext {
	@NonNull
	private final String packageName;
	@NonNull
	private final List<ImportDeclaration> imports;
	@NonNull
	private final Iterable<TypeParameter> typeParameters;
	@NonNull
	private final SearchPath searchPath;

	private ResolutionContext(String packageName, List<ImportDeclaration> imports, Iterable<TypeParameter> typeParameters, SearchPath searchPath) {
		this.packageName = packageName;
		this.imports = imports;
		this.typeParameters = typeParameters;
		this.searchPath = searchPath;
	}

	public static ResolutionContext of(String packageName, List<ImportDeclaration> imports, Iterable<TypeParameter> typeParameters, SearchPath searchPath) {
		return new ResolutionContext(packageName, imports, typeParameters, searchPath);
	}

	public static ResolutionContext of(Node targetNode, Node outerClassNode, SearchPath searchPath) {
		CompilationUnit cu = NodeUtil.getParentNode(outerClassNode, CompilationUnit.class);
		String packageName = NodeUtil.qualifiedName(cu.getPackageDeclaration().get().getName());
		List<TypeParameter> typeParameters = NodeUtil.getTypeParameters(targetNode);

		return new ResolutionContext(packageName, cu.getImports(), typeParameters, searchPath);
	}

	public static ResolutionContext of(Node node, SearchPath searchPath) {
		return of(node, node, searchPath);
	}

	private static boolean hasPackages(String name) {
		// Guesses whether input name includes packages or is just classes
		return !Character.isUpperCase(name.charAt(0)) && name.indexOf('.') != -1;
	}

	public static String extractGeneric(String name) {
		int leftBracket = name.indexOf('<');
		int rightBracket = name.indexOf('>');

		if (leftBracket == -1 && rightBracket == -1)
			return null;

		if (leftBracket != -1 && leftBracket < rightBracket)
			return name.substring(leftBracket + 1, rightBracket);

		throw new TransformationException("Mismatched angled brackets in: " + name);
	}

	public static String extractReal(String name) {
		int bracket = name.indexOf('<');
		return bracket == -1 ? name : name.substring(0, bracket);
	}

	static Type sanityCheck(Type type) {
		if (type.isClassType() && (type.getClassName().endsWith(".") || !type.getClassName().contains("."))) {
			throw new TransformationException("Unexpected class name (incorrect dots) in type: " + type);
		}

		return type;
	}

	private static String toString(ImportDeclaration importDeclaration) {
		return (importDeclaration.isStatic() ? "static " : "") + classOf(importDeclaration) + (importDeclaration.isAsterisk() ? ".*" : "");
	}

	private static String classOf(ImportDeclaration importDeclaration) {
		return NodeUtil.qualifiedName(importDeclaration.getName());
	}

	public static com.github.javaparser.ast.type.Type typeToJavaParserType(Type t) {
		if (t.isPrimitiveType()) {
			val primitiveName = t.getPrimitiveTypeName();
			if (primitiveName.equals(Type.DescriptorType.VOID.getPrimitiveName()))
				return new VoidType();
			return new PrimitiveType(JVMUtil.searchEnum(PrimitiveType.Primitive.class, t.getPrimitiveTypeName()));
		}

		if (t.isArrayType())
			return new ArrayType(typeToJavaParserType(t.getArrayContainedType()));

		if (t.isPrimitiveType())
			return new PrimitiveType();

		if (!t.isClassType())
			throw new UnsupportedOperationException(t + " is not a class type");

		if (t.isTypeParameter())
			return new ClassOrInterfaceType(t.getTypeParameterName());

		val type = new ClassOrInterfaceType(t.getClassName());

		if (t.hasTypeArguments())
			type.setTypeArguments(NodeList.nodeList(t.getTypeArguments().stream().map(ResolutionContext::typeToJavaParserType).collect(Collectors.toList())));

		return type;
	}

	public Type resolve(com.github.javaparser.ast.type.Type type) {
		if (type instanceof PrimitiveType) {
			return new Type(JVMUtil.primitiveTypeToDescriptor(((PrimitiveType) type).getType().name().toLowerCase()));
		} else if (type instanceof VoidType) {
			return new Type("V");
		} else {
			// TODO: 23/01/2016 Is this behaviour correct?
			return resolve(type.asString());
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

		int arrayCount = 0;
		while (name.length() > 1 && name.lastIndexOf("[]") == name.length() - 2) {
			arrayCount++;
			name = name.substring(0, name.length() - 2);
		}

		String real = extractReal(name);
		Type type = resolveReal(real);

		String generic = extractGeneric(name);
		List<Type> genericTypes = null;

		if ("".equals(generic)) {
			// TODO: properly mark this so we keep <> instead of becoming raw type
			generic = null;
		}
		if (generic != null) {
			genericTypes = Splitter.commaSplitter.split(generic).map(this::resolve).collect(Collectors.toList());
		}

		if (type == null || (generic != null && (genericTypes.isEmpty() || genericTypes.stream().anyMatch(Objects::isNull))))
			throw new TransformationException("Couldn't resolve name: " + name +
				"\nFound real type: " + type +
				"\nGeneric types: " + genericTypes +
				"\nImports:" + imports.stream().map(ResolutionContext::toString).collect(Collectors.toList()) +
				"\nSearchPath: " + searchPath
			);

		if (generic != null) {
			type = type.withTypeArguments(genericTypes);
		}

		if (arrayCount != 0) {
			type = type.withArrayCount(arrayCount);
		}

		return sanityCheck(type);
	}

	private Type resolveReal(String name) {
		String primitive = JVMUtil.primitiveTypeToDescriptor(name, true);
		if (primitive != null)
			return new Type(primitive, null);

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
			if (anImport.isAsterisk() || anImport.isStatic())
				continue;

			String importName = classOf(anImport);
			if (importName.endsWith(dotName)) {
				return Type.of(importName);
			}
		}

		Type type = resolveIfExists(packageName + '.' + name);
		if (type != null) {
			return type;
		}

		for (ImportDeclaration anImport : imports) {
			if (!anImport.isAsterisk() || anImport.isStatic())
				continue;

			type = resolveIfExists(classOf(anImport) + '.' + name);
			if (type != null) {
				return type;
			}
		}

		type = resolveIfExists("java.lang." + name);
		if (type != null) {
			return type;
		}

		if (!hasPackages(name) && !Objects.equals(System.getProperty("JarTransformer.allowDefaultPackage"), "true")) {
			return null;
		}

		return Type.of(name);
	}

	private Type resolveIfExists(String s) {
		if (s.startsWith("java.") || s.startsWith("javax.")) {
			try {
				return Type.of(Class.forName(s).getName());
			} catch (ClassNotFoundException ignored) {
			}
		}
		if (searchPath.exists(s))
			return Type.of(s);
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
			String typeName = typeParameter.getName().asString();
			if (typeName.equals(name)) {
				val bounds = typeParameter.getTypeBound();
				String extends_ = "Ljava/lang/Object;";

				if (bounds != null && !bounds.isEmpty()) {
					if (bounds.size() == 1) {
						ClassOrInterfaceType scope = bounds.get(0).getScope().orElse(null);
						if (scope != null) {
							extends_ = resolve(scope.getName().asString()).descriptor;
						}
					} else {
						throw new TransformationException("Bounds must have one object, found: " + bounds);
					}
				}

				return new Type(extends_, "T" + typeName + ";");
			}
		}

		return null;
	}

	public String typeToString(Type t) {
		return typeToString(t, true);
	}

	public String typeToString(Type t, boolean unresolve) {
		if (t.isPrimitiveType()) {
			return t.getPrimitiveTypeName();
		}
		if (t.isTypeParameter()) {
			return t.getTypeParameterName();
		}
		String className = t.getClassName();

		if (unresolve)
			className = typeToJavaParserType(className);

		if (t.hasTypeArguments())
			className += '<' + Joiner.on(", ").join(t.getTypeArguments().stream().map(this::typeToString)) + '>';

		return className;
	}

	public String typeToJavaParserType(String className) {
		for (ImportDeclaration anImport : imports) {
			if (anImport.isAsterisk() || anImport.isStatic())
				continue;

			String importName = NodeUtil.qualifiedName(anImport.getName());
			if (className.startsWith(importName)) {
				return className.replace(importName + ".", "");
			}
		}

		return className;
	}

	public TypeVariable resolveTypeVariable(TypeParameter typeParameter) {
		Type bound;
		val typeBound = typeParameter.getTypeBound();
		if (typeBound.size() == 1)
			bound = resolve(typeBound.get(0));
		else if (typeParameter.getTypeBound().isEmpty())
			bound = Type.OBJECT;
		else
			throw new IllegalArgumentException("Can't resolve type variable from " + typeParameter + " with multiple bounds");

		return new TypeVariable(typeParameter.getName().asString(), bound);
	}

	public TypeParameter unresolveTypeVariable(TypeVariable typeVariable) {
		if (typeVariable.getBounds().equals(Type.OBJECT))
			return new TypeParameter(typeVariable.getName(), NodeList.nodeList());

		return new TypeParameter(typeVariable.getName(), NodeList.nodeList((ClassOrInterfaceType) typeToJavaParserType(typeVariable.getBounds())));
	}
}
