package dev.minco.javatransformer.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.NonNull;
import lombok.val;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.VoidType;

import dev.minco.javatransformer.api.AccessFlags;
import dev.minco.javatransformer.api.ClassInfo;
import dev.minco.javatransformer.api.ClassMember;
import dev.minco.javatransformer.api.ClassPath;
import dev.minco.javatransformer.api.FieldInfo;
import dev.minco.javatransformer.api.MethodInfo;
import dev.minco.javatransformer.api.Parameter;
import dev.minco.javatransformer.api.TransformationException;
import dev.minco.javatransformer.api.Type;
import dev.minco.javatransformer.api.TypeVariable;
import dev.minco.javatransformer.internal.javaparser.Expressions;
import dev.minco.javatransformer.internal.util.JVMUtil;
import dev.minco.javatransformer.internal.util.Joiner;
import dev.minco.javatransformer.internal.util.NodeUtil;
import dev.minco.javatransformer.internal.util.Splitter;

@Getter
public class ResolutionContext {
	@NonNull
	private final String packageName;
	@NonNull
	private final List<ImportDeclaration> imports;
	@NonNull
	private final Iterable<TypeParameter> typeParameters;
	@NonNull
	private final ClassPath classPath;
	@Nullable
	private final ClassMember classMember;

	public ResolutionContext(@NonNull String packageName, @NonNull List<ImportDeclaration> imports, @NonNull Iterable<TypeParameter> typeParameters, @NonNull ClassPath classPath, @Nullable ClassMember classMember) {
		this.packageName = packageName;
		this.imports = imports;
		this.typeParameters = typeParameters;
		this.classPath = classPath;
		this.classMember = classMember;
	}

	public static ResolutionContext of(Node targetNode, Node outerClassNode, ClassPath classPath, ClassMember classMember) {
		CompilationUnit cu = NodeUtil.getParentNode(outerClassNode, CompilationUnit.class);
		String packageName = NodeUtil.qualifiedName(cu.getPackageDeclaration().get().getName());
		List<TypeParameter> typeParameters = NodeUtil.getTypeParameters(targetNode);

		return new ResolutionContext(packageName, cu.getImports(), typeParameters, classPath, classMember);
	}

	private static boolean hasPackages(String name) {
		// Guesses whether input name includes packages or is just classes
		return !Character.isUpperCase(name.charAt(0)) && name.indexOf('.') != -1;
	}

	@Nullable
	public static String extractGeneric(@Nullable String name) {
		if (name == null)
			return null;

		int leftBracket = name.indexOf('<');
		int rightBracket = name.lastIndexOf('>');

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

		if (!t.isClassType())
			throw new UnsupportedOperationException(t + " is not a class type");

		if (t.isTypeParameter())
			return nonGenericClassOrInterfaceType(t.getTypeParameterName());

		val type = nonGenericClassOrInterfaceType(t.getClassName());

		if (t.hasTypeArguments())
			type.setTypeArguments(NodeList.nodeList(t.getTypeArguments().stream().map(ResolutionContext::typeToJavaParserType).collect(Collectors.toList())));

		return type;
	}

	@SuppressWarnings("deprecation")
	public static ClassOrInterfaceType nonGenericClassOrInterfaceType(String name) {
		return new ClassOrInterfaceType(name);
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
	 *
	 * <ul>
	 * <li>ArrayList -&gt; Ljava/util/ArrayList;</li>
	 * <li>T -&gt; TT;</li>
	 * <li>boolean -&gt; Z</li>
	 * </ul>
	 *
	 * @param name Name to resolve
	 * @return Type containing resolved name with descriptor/signature
	 */
	@Contract(value = "!null -> !null; null -> fail", pure = true)
	@NonNull
	public Type resolve(@NonNull String name) {
		if (name.equals("?")) {
			return Type.WILDCARD;
		}

		int arrayCount = 0;
		if (name.endsWith("...")) {
			arrayCount++;
			name = name.substring(0, name.length() - 3);
		}
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
				"\nPackage: " + packageName +
				"\nImports:" + imports.stream().map(ResolutionContext::toString).collect(Collectors.toList()) +
				"\nClassPath: " + classPath);

		if (generic != null) {
			type = type.withTypeArguments(genericTypes);
		}

		if (arrayCount != 0) {
			type = type.withArrayCount(arrayCount);
		}

		return sanityCheck(type);
	}

	/**
	 * super -> super class type this -> this class type local variable -> local var's type field -> field type
	 *
	 * @param name
	 * @return
	 */
	public Type resolveNameInExpressionContext(String name) {
		switch (name) {
			case "super":
				return classMember.getClassInfo().getSuperType();
			case "this":
				// TODO special case in this class to skip going via ClassPath when getting class info matching ClassMember
				return classMember.getClassInfo().getType();
		}

		// TODO look for locals
		if (classMember instanceof SourceInfo.CallableDeclarationWrapper<?>) {
			val cd = (SourceInfo.CallableDeclarationWrapper<?>) classMember;

			for (Parameter parameter : cd.getParameters()) {
				if (parameter.name != null && parameter.name.equals(name)) {
					return parameter.type;
				}
			}

			// TODO: direct node access here feels messy, abstraction? eh probably not needed
			// TODO: don't support local type inference (val or var)
			val body = cd.getBody();
			if (body != null) {
				for (VariableDeclarationExpr variableDeclarationExpr : NodeUtil.findWithinMethodScope(VariableDeclarationExpr.class, cd.getBody())) {
					for (VariableDeclarator variable : variableDeclarationExpr.getVariables()) {
						if (variable.getNameAsString().equals(name)) {
							if (variable.getType().isVarType()) {
								return Expressions.expressionToType(variable.getInitializer().get(), this, true);
							}
							val type = resolve(variable.getType());
							if (type.equals(Type.of("lombok.val"))) {
								val initializer = variable.getInitializer().orElse(null);
								if (initializer != null) {
									return Expressions.expressionToType(initializer, this, true);
								}
							}
							return type;
						}
					}
				}
			}
		}

		{
			Type fieldType = resolveFieldType(classMember.getClassInfo().getType(), name);
			if (fieldType != Type.UNKNOWN) {
				return fieldType;
			}
		}

		{
			Type importedType = resolve(name);
			if (importedType != Type.UNKNOWN) {
				return Type.staticMetaclassOf(importedType);
			}
		}

		return Type.UNKNOWN;
	}

	@Nullable
	public MethodInfo resolveMethodCallType(Type scope, String name, Supplier<List<Type>> usedTypes) {
		boolean staticContext = false;
		if (scope.isStaticMetaClass()) {
			scope = scope.getTypeArguments().get(0);
			staticContext = true;
		}

		if (name.equals("getClass") && !staticContext) {
			val smi = SimpleMethodInfo.of(
				new AccessFlags(AccessFlags.ACC_PUBLIC),
				Collections.emptyList(),
				Type.CLAZZ.withTypeArgument(resolveNameInExpressionContext("this")),
				"getClass",
				Collections.emptyList());
			smi.classInfo = getClassPath().getClassInfo(scope.getClassName());
			return smi;
		}

		List<MethodInfo> potentials = new ArrayList<>();
		visitMethods(scope, name, potentials, staticContext);
		if (potentials.size() == 1) {
			return potentials.get(0);
		}

		for (MethodInfo potential : potentials) {
			if (paramTypesMatch(potential.getParameters(), usedTypes.get(), potential.getAccessFlags().has(AccessFlags.ACC_VARARGS))) {
				return potential;
			}
		}

		return null;
	}

	private void visitMethods(Type scope, String name, List<MethodInfo> potentials, boolean staticContext) {
		while (true) {
			val ci = getClassPath().getClassInfo(scope.getClassName());

			if (ci == null) {
				throw new TransformationException("Couldn't get ClassInfo for {" + scope.getClassName() + "} while searching for method {" + name + "} on {" + scope + "}");
			}

			if (staticContext) {
				ci.getMethods().filter(it -> it.getName().equals(name) && it.getAccessFlags().has(AccessFlags.ACC_STATIC)).forEach(potentials::add);
			} else {
				ci.getMethods().filter(it -> it.getName().equals(name)).forEach(potentials::add);
			}

			// since default interface methods got added, always have to look in interfaces
			// if (ci.getAccessFlags().has(AccessFlags.ACC_ABSTRACT) || ci.getAccessFlags().has(AccessFlags.ACC_INTERFACE)) {
			for (Type interfaceType : ci.getInterfaceTypes()) {
				visitMethods(interfaceType, name, potentials, staticContext);
			}

			val superType = ci.getSuperType();

			if (superType == null) {
				return;
			}

			scope = superType;
		}
	}

	private boolean paramTypesMatch(List<Parameter> parameters, List<Type> types, boolean allowVarargs) {
		int params = parameters.size();

		boolean canVarargs = false;
		if (allowVarargs && params > 0 && types.size() >= params - 1) {
			val lastType = parameters.get(params - 1).type;
			if (lastType.isArrayType()) {
				canVarargs = true;
				val contained = lastType.getArrayContainedType();
				for (int i = params - 1; i < types.size(); i++) {
					if (!isAssignableFrom(types.get(i), contained)) {
						canVarargs = false;
						break;
					}
				}
				if (canVarargs) {
					params--;
				}
			}
		}

		if (types.size() != params && !canVarargs) {
			return false;
		}

		for (int i = 0; i < params; i++) {
			if (!isAssignableFrom(types.get(i), parameters.get(i).type)) {
				return false;
			}
		}
		return true;
	}

	public Type resolveFieldType(Type scope, String name) {
		boolean staticContext = false;
		if (scope.isStaticMetaClass()) {
			scope = scope.getTypeArguments().get(0);
			staticContext = true;
		}

		while (true) {
			val ci = getClassPath().getClassInfo(scope.getClassName());

			if (ci == null) {
				throw new TransformationException("Couldn't get ClassInfo for {" + scope.getClassName() + "} while searching for field {" + name + "} on {" + scope + "}");
			}

			FieldInfo field;
			if (staticContext) {
				field = ci.getFields().filter(it -> it.getName().equals(name) && it.getAccessFlags().has(AccessFlags.ACC_STATIC)).findFirst().orElse(null);
			} else {
				field = ci.getFields().filter(it -> it.getName().equals(name)).findFirst().orElse(null);
			}

			if (field != null) {
				return field.getType();
			}

			val superType = ci.getSuperType();

			if (superType == null) {
				return Type.UNKNOWN;
			}

			scope = superType;
		}
	}

	public boolean isAssignableFrom(Type from, Type to) {
		if (to.descriptor.equals(from.descriptor) ||
		// auto-boxing -> anything to object is okay?
			(to.isClassType() && to.getClassName().equals("java.lang.Object"))) {
			return true;
		}

		if (from == Type.LAMBDA) {
			// TODO need to get params to this point and match properly
			return true;
		}

		if (from.isArrayType() && to.isArrayType() && isAssignableFrom(from.getArrayContainedType(), to.getArrayContainedType())) {
			return true;
		}

		if (from.isPrimitiveType() && to.isPrimitiveType()) {
			val fromWidth = from.getDescriptorType().getByteWidth();
			val toWidth = to.getDescriptorType().getByteWidth();
			if (toWidth != null && fromWidth != null && toWidth >= fromWidth) {
				return true;
			}

			if (to.equals(Type.DOUBLE) && from.equals(Type.FLOAT)) {
				return true;
			}
		}

		if (from.isClassType()) {
			for (Type extendedType : allExtendedTypes(from)) {
				if (extendedType.descriptor.equals(to.descriptor)) {
					return true;
				}
			}
		}

		return false;
	}

	private List<Type> allExtendedTypes(Type type) {
		val results = new ArrayList<Type>();

		Type current = type;
		while (current != null && !current.equals(Type.OBJECT)) {
			results.add(current);

			val ci = getClassPath().getClassInfo(current.getClassName());

			for (Type interfaceType : ci.getInterfaceTypes()) {
				results.addAll(allExtendedTypes(interfaceType));
			}

			current = ci.getSuperType();
		}

		return results;
	}

	@Nullable
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

	@Nullable
	private Type resolveClassType(String name) {
		String dotName = name;
		String preDotName = null;
		String postDotName = null;
		if (name.indexOf('.') != -1) {
			val index = name.indexOf('.');
			preDotName = name.substring(0, index);
			postDotName = name.substring(index);
		} else {
			dotName = '.' + name;
		}

		// inner class in current class
		if (preDotName == null && classMember != null) {
			ClassInfo ci = classMember.getClassInfo();

			if (ci instanceof SourceInfo) {
				TypeDeclaration<?> node = ((SourceInfo) ci).getJavaParserType();
				while (node != null) {
					Type type = resolveIfExists(packageName + '.' + node.getNameAsString() + '$' + name);
					if (type != null) {
						return type;
					}
					node = NodeUtil.getParentNode(node, TypeDeclaration.class);
				}
			}

			/*
			boolean continueToNextType = true;
			while (true) {
				Type type = resolveIfExists(ci.getType().getClassName() + '$' + name);
				if (type != null) {
					return type;
				}
				if (!continueToNextType) {
					break;
				}
				ci = getClassPath().getClassInfo(ci.getSuperType().getClassName());
				if (ci == null || !ci.getType().getClassName().contains("$")) {
					continueToNextType = false;
				}
			}
			 */
		}

		for (ImportDeclaration anImport : imports) {
			if (anImport.isAsterisk() || anImport.isStatic())
				continue;

			String importName = classOf(anImport);
			if (importName.endsWith(dotName)) {
				return Type.of(importName);
			}

			// inner class in imported class
			if (preDotName != null && importName.endsWith(preDotName)) {
				val type = resolveIfExists(importName + postDotName.replace('.', '$'));
				if (type != null) {
					return type;
				}
			}
		}

		Type type = resolveIfExists(packageName + '.' + name.replace('.', '$'));
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

	@Nullable
	private Type resolveIfExists(String s) {
		if (classPath.classExists(s))
			return Type.of(s);
		return null;
	}

	/**
	 * If we have the type parameter "A extends StringBuilder", then "A" is resolved to a type with:
	 *
	 * <ul>
	 * <li>descriptor: Ljava/lang/StringBuilder;</li>
	 * <li>signature: TA;</li>
	 * </ul>
	 */
	@Nullable
	private Type resolveTypeParameterType(String name) {
		for (TypeParameter typeParameter : typeParameters) {
			String typeName = typeParameter.getName().asString();
			if (typeName.equals(name)) {
				val bounds = typeParameter.getTypeBound();
				String extends_ = "Ljava/lang/Object;";

				if (bounds != null && !bounds.isEmpty()) {
					if (bounds.size() == 1) {
						extends_ = resolve(bounds.get(0)).descriptor;
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
