package me.nallar.javatransformer.internal;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.val;
import me.nallar.javatransformer.api.*;
import me.nallar.javatransformer.api.Parameter;
import me.nallar.javatransformer.internal.util.AnnotationParser;
import me.nallar.javatransformer.internal.util.JVMUtil;
import me.nallar.javatransformer.internal.util.NodeUtil;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

@Data
@AllArgsConstructor
@SuppressWarnings("unchecked")
public class SourceInfo implements ClassInfoStreams {
	private final Supplier<ClassOrInterfaceDeclaration> type;
	@Getter(lazy = true)
	private final String packageName = getPackageNameInternal();
	@Getter(lazy = true)
	private final ResolutionContext context = getResolutionContextInternal();
	@Getter(lazy = true)
	private final List<Annotation> annotations = getAnnotationsInternal();
	private String className;

	private String getPackageNameInternal() {
		return NodeUtil.getParentNode(type.get(), CompilationUnit.class).getPackage().getName().getName();
	}

	private ResolutionContext getResolutionContextInternal() {
		return ResolutionContext.of(type.get());
	}

	@Override
	public String getName() {
		return className;
	}

	@Override
	public void setName(String name) {
		String packageName = getPackageName();
		if (name.startsWith(packageName)) {
			type.get().setName(name.replace(packageName, ""));
		} else {
			throw new RuntimeException("Name '" + name + "' must be in package: " + packageName);
		}
	}

	@Override
	public AccessFlags getAccessFlags() {
		return new AccessFlags(type.get().getModifiers());
	}

	@Override
	public void setAccessFlags(AccessFlags accessFlags) {
		type.get().setModifiers(accessFlags.access);
	}

	@Override
	public void add(MethodInfo description) {
		MethodDeclaration methodDeclaration = new MethodDeclaration();
		val wrapper = new MethodDeclarationWrapper(methodDeclaration);
		wrapper.setAll(description);
	}

	@Override
	public void add(FieldInfo field) {
		FieldDeclaration fieldDeclaration = new FieldDeclaration();
		val vars = new ArrayList<VariableDeclarator>();
		vars.add(new VariableDeclarator(new VariableDeclaratorId("unknown")));
		fieldDeclaration.setVariables(vars);
		FieldDeclarationWrapper wrapper = new FieldDeclarationWrapper(fieldDeclaration);
		wrapper.setAll(field);
	}

	@Override
	public void remove(MethodInfo method) {
		MethodDeclarationWrapper methodDeclarationWrapper = !(method instanceof MethodDeclarationWrapper) ? (MethodDeclarationWrapper) get(method) : (MethodDeclarationWrapper) method;

		if (methodDeclarationWrapper == null)
			throw new RuntimeException("Method " + method + " can not be removed as it is not present");

		type.get().getMembers().remove(methodDeclarationWrapper.declaration);
	}

	@Override
	public void remove(FieldInfo field) {
		FieldDeclarationWrapper fieldDeclarationWrapper = !(field instanceof FieldDeclarationWrapper) ? (FieldDeclarationWrapper) get(field) : (FieldDeclarationWrapper) field;

		if (fieldDeclarationWrapper == null)
			throw new RuntimeException("Field " + field + " can not be removed as it is not present");

		type.get().getMembers().remove(fieldDeclarationWrapper.declaration);
	}

	@Override
	public Type getSuperType() {
		val extends_ = type.get().getExtends();

		if (extends_ == null || extends_.isEmpty())
			return null;

		return getContext().resolve(extends_.get(0));
	}

	@Override
	public List<Type> getInterfaceTypes() {
		return type.get().getImplements().stream().map(getContext()::resolve).collect(Collectors.toList());
	}

	public Stream<MethodInfo> getMethodStream() {
		return type.get().getMembers().stream()
			.filter(x -> x instanceof MethodDeclaration)
			.map(x -> new MethodDeclarationWrapper((MethodDeclaration) x));
	}

	public Stream<FieldInfo> getFieldStream() {
		return type.get().getMembers().stream()
			.filter(x -> x instanceof FieldDeclaration)
			.map(x -> new FieldDeclarationWrapper((FieldDeclaration) x));
	}

	private com.github.javaparser.ast.type.Type toType(Type t) {
		String name = getContext().unresolve(t);
		if (t.isPrimitiveType()) {
			return new PrimitiveType(JVMUtil.searchEnum(PrimitiveType.Primitive.class, name));
		} else {
			return new ClassOrInterfaceType(name);
		}
	}

	private com.github.javaparser.ast.type.Type setType(Type newType, com.github.javaparser.ast.type.Type currentType) {
		val newType_ = toType(newType);

		if (currentType instanceof ClassOrInterfaceType && newType_ instanceof ClassOrInterfaceType) {
			val annotations = currentType.getAnnotations();
			if (annotations != null && !annotations.isEmpty())
				newType_.setAnnotations(annotations);
		}
		return newType_;
	}

	private List<Annotation> getAnnotationsInternal() {
		return getAnnotationsInternal(type.get().getAnnotations());
	}

	private List<Annotation> getAnnotationsInternal(List<AnnotationExpr> l) {
		return l.stream().map(AnnotationParser::annotationFromAnnotationExpr).collect(Collectors.toList());
	}

	@Override
	public ClassInfo getClassInfo() {
		return SourceInfo.this;
	}

	class FieldDeclarationWrapper implements FieldInfo {
		private final FieldDeclaration declaration;
		private ResolutionContext context;

		FieldDeclarationWrapper(FieldDeclaration declaration) {
			this.declaration = declaration;
			if (declaration.getVariables().size() != 1) {
				throw new UnsupportedOperationException("Not yet implemented: multiple variables in one field decl.");
			}
		}

		private ResolutionContext getContext() {
			if (context != null) return context;
			return context = ResolutionContext.of(declaration);
		}

		@Override
		public String getName() {
			return declaration.getVariables().get(0).getId().getName();
		}

		@Override
		public void setName(String name) {
			declaration.getVariables().get(0).getId().setName(name);
		}

		@Override
		public AccessFlags getAccessFlags() {
			return new AccessFlags(declaration.getModifiers());
		}

		@Override
		public void setAccessFlags(AccessFlags accessFlags) {
			declaration.setModifiers(accessFlags.access);
		}

		@Override
		public Type getType() {
			return getContext().resolve(declaration.getType());
		}

		@Override
		public void setType(Type type) {
			declaration.setType(SourceInfo.this.setType(type, declaration.getType()));
		}

		@Override
		public List<Annotation> getAnnotations() {
			return SourceInfo.this.getAnnotationsInternal(declaration.getAnnotations());
		}

		@Override
		public ClassInfo getClassInfo() {
			return SourceInfo.this;
		}
	}

	class MethodDeclarationWrapper implements MethodInfo {
		private final MethodDeclaration declaration;
		private ResolutionContext context;

		public MethodDeclarationWrapper(MethodDeclaration declaration) {
			this.declaration = declaration;
		}

		private ResolutionContext getContext() {
			if (context != null) return context;
			return context = ResolutionContext.of(declaration);
		}

		@Override
		public Type getReturnType() {
			return getContext().resolve(declaration.getType());
		}

		@Override
		public void setReturnType(Type type) {
			declaration.setType(setType(type, declaration.getType()));
		}

		@Override
		public List<Parameter> getParameters() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setParameters(List<Parameter> parameters) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getName() {
			return declaration.getName();
		}

		@Override
		public void setName(String name) {
			declaration.setName(name);
		}

		@Override
		public AccessFlags getAccessFlags() {
			return new AccessFlags(declaration.getModifiers());
		}

		@Override
		public void setAccessFlags(AccessFlags accessFlags) {
			declaration.setModifiers(accessFlags.access);
		}

		@Override
		public List<Annotation> getAnnotations() {
			return SourceInfo.this.getAnnotationsInternal(declaration.getAnnotations());
		}

		@Override
		public ClassInfo getClassInfo() {
			return SourceInfo.this;
		}
	}
}
