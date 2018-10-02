package org.minimallycorrect.javatransformer.internal.javaparser;

import java.util.*;
import java.util.stream.Collectors;

import lombok.ToString;
import lombok.val;

import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.ResolvedArrayType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.ResolvedTypeTransformer;
import com.github.javaparser.resolution.types.parametrization.ResolvedTypeParametersMap;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;

import org.minimallycorrect.javatransformer.api.AccessFlags;
import org.minimallycorrect.javatransformer.api.FieldInfo;
import org.minimallycorrect.javatransformer.api.Type;
import org.minimallycorrect.javatransformer.internal.ByteCodeInfo;
import org.minimallycorrect.javatransformer.internal.util.JVMUtil;

public class AsmResolvedTypes {
	public static ResolvedReferenceTypeDeclaration fromByteCodeInfo(TypeSolver context, ByteCodeInfo bci) {
		return new AsmResolvedTypes.AsmResolvedClassDeclaration(context, bci);
	}

	public static ResolvedType fromType(TypeSolver context, Type type) {
		if (type.isArrayType()) {
			return new ResolvedArrayType(fromType(context, type.getArrayContainedType()));
		}

		if (type.isClassType()) {
			return new AsmResolvedReferenceType(context, type, context.solveType(type.getClassName()));
		}

		throw new UnsupportedOperationException("TODO " + type);
	}

	public static Type convertResolvedTypeToType(ResolvedType resolvedType) {
		if (resolvedType.isPrimitive()) {
			return new Type(JVMUtil.primitiveTypeToDescriptor(resolvedType.asPrimitive().describe()), null);
		} else if (resolvedType.isReferenceType()) {
			val rt = resolvedType.asReferenceType();
			val tp = rt.typeParametersMap();
			if (tp.isEmpty()) {
				return Type.of(rt.getQualifiedName());
			}
		}

		throw new UnsupportedOperationException("TODO " + resolvedType);
	}

	@ToString
	public static class AsmResolvedClassDeclaration implements ResolvedClassDeclaration {
		TypeSolver typeSolver;
		ByteCodeInfo bci;

		public AsmResolvedClassDeclaration(TypeSolver typeSolver, ByteCodeInfo bci) {
			this.typeSolver = typeSolver;
			this.bci = bci;
		}

		@Override
		public List<ResolvedReferenceType> getAncestors(boolean acceptIncompleteList) {
			List<ResolvedReferenceType> ancestors = new ArrayList<>();
			try {
				ResolvedReferenceType superClass = getSuperClass();
				if (superClass != null) {
					ancestors.add(superClass);
				}
			} catch (UnsolvedSymbolException e) {
				if (!acceptIncompleteList) {
					// we only throw an exception if we require a complete list; otherwise, we attempt to continue gracefully
					throw e;
				}
			}
			try {
				ancestors.addAll(getInterfaces());
			} catch (UnsolvedSymbolException e) {
				if (!acceptIncompleteList) {
					// we only throw an exception if we require a complete list; otherwise, we attempt to continue gracefully
					throw e;
				}
			}
			return ancestors;
		}

		@Override
		public List<ResolvedFieldDeclaration> getAllFields() {
			return bci.getFields().map(it -> new AsmResolvedFieldDeclaration(this, it)).collect(Collectors.toList());
		}

		@Override
		public Set<ResolvedMethodDeclaration> getDeclaredMethods() {
			val result = new HashSet<ResolvedMethodDeclaration>();
			return result;
		}

		@Override
		public Set<MethodUsage> getAllMethods() {
			val result = new HashSet<MethodUsage>();
			return result;
		}

		@Override
		public boolean isAssignableBy(ResolvedType type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isAssignableBy(ResolvedReferenceTypeDeclaration other) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasDirectlyAnnotation(String qualifiedName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isFunctionalInterface() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ResolvedReferenceType getSuperClass() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<ResolvedReferenceType> getInterfaces() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<ResolvedReferenceType> getAllSuperClasses() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<ResolvedReferenceType> getAllInterfaces() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<ResolvedConstructorDeclaration> getConstructors() {
			throw new UnsupportedOperationException();
		}

		@Override
		public AccessSpecifier accessSpecifier() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<ResolvedReferenceTypeDeclaration> containerType() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getPackageName() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getClassName() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getQualifiedName() {
			return bci.getClassName();
		}

		@Override
		public String getName() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<ResolvedTypeParameterDeclaration> getTypeParameters() {
			val type = bci.getType();
			if (!type.hasTypeArguments()) {
				return Collections.emptyList();
			}
			val list = new ArrayList<ResolvedTypeParameterDeclaration>();
			for (val arg : type.getTypeArguments()) {
				list.add(new AsmResolvedTypeParameterDeclaration(arg));
			}
			return list;
		}

		@ToString
		private class AsmResolvedMethodDeclaration implements ResolvedMethodDeclaration {
			ByteCodeInfo.MethodNodeInfo mni;

			public AsmResolvedMethodDeclaration(ByteCodeInfo.MethodNodeInfo mni) {
				this.mni = mni;
			}

			@Override
			public ResolvedType getReturnType() {
				return null;
			}

			@Override
			public boolean isAbstract() {
				return mni.getAccessFlags().has(AccessFlags.ACC_ABSTRACT);
			}

			@Override
			public boolean isDefaultMethod() {
				return AsmResolvedClassDeclaration.this.bci.getAccessFlags().has(AccessFlags.ACC_INTERFACE) &&
					!isAbstract();
			}

			@Override
			public boolean isStatic() {
				return mni.getAccessFlags().has(AccessFlags.ACC_STATIC);
			}

			@Override
			public ResolvedReferenceTypeDeclaration declaringType() {
				return AsmResolvedClassDeclaration.this;
			}

			@Override
			public int getNumberOfParams() {
				return mni.getParameters().size();
			}

			@Override
			public ResolvedParameterDeclaration getParam(int i) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int getNumberOfSpecifiedExceptions() {
				return mni.node.exceptions.size();
			}

			@Override
			public ResolvedType getSpecifiedException(int index) {
				throw new UnsupportedOperationException();
			}

			@Override
			public AccessSpecifier accessSpecifier() {
				return AccessSpecifierUtil.fromAccessFlags(mni.getAccessFlags());
			}

			@Override
			public String getName() {
				return mni.getName();
			}

			@Override
			public List<ResolvedTypeParameterDeclaration> getTypeParameters() {
				throw new UnsupportedOperationException();
			}
		}

		@ToString
		private class AsmResolvedTypeParameterDeclaration implements ResolvedTypeParameterDeclaration {
			Type arg;

			AsmResolvedTypeParameterDeclaration(Type arg) {
				this.arg = arg;
			}

			@Override
			public String getName() {
				return arg.getTypeParameterName();
			}

			@Override
			public String getContainerQualifiedName() {
				return AsmResolvedClassDeclaration.this.getQualifiedName();
			}

			@Override
			public String getContainerId() {
				return getContainerQualifiedName();
			}

			@Override
			public ResolvedTypeParametrizable getContainer() {
				return AsmResolvedClassDeclaration.this;
			}

			@Override
			public List<Bound> getBounds() {
				throw new UnsupportedOperationException();
				/*
				List<Bound> bounds = new ArrayList<>();
				val bci = AsmResolvedClassDeclaration.this.bci;
				val superType = bci.getSuperType();
				if (superType != null && !superType.equals(Type.OBJECT)) {
					throw new UnsupportedOperationException(superType.toString());
				}
				for (val it : bci.getInterfaceTypes()) {
					throw new UnsupportedOperationException(it.toString());
				}
				return bounds;*/
			}

			@Override
			public Optional<ResolvedReferenceTypeDeclaration> containerType() {
				return Optional.of(AsmResolvedClassDeclaration.this);
			}
		}

		@ToString
		private class AsmResolvedFieldDeclaration implements ResolvedFieldDeclaration {
			AsmResolvedClassDeclaration container;
			FieldInfo fi;

			public AsmResolvedFieldDeclaration(AsmResolvedClassDeclaration container, FieldInfo field) {
				this.container = container;
				this.fi = field;
			}

			@Override
			public boolean isStatic() {
				return fi.getAccessFlags().has(AccessFlags.ACC_STATIC);
			}

			@Override
			public ResolvedTypeDeclaration declaringType() {
				return container;
			}

			@Override
			public AccessSpecifier accessSpecifier() {
				return AccessSpecifierUtil.fromAccessFlags(fi.getAccessFlags());
			}

			@Override
			public ResolvedType getType() {
				return fromType(container.typeSolver, fi.getType());
				//return new AsmResolvedType(fi.getType());
				//throw new UnsupportedOperationException();
				//return container.typeSolver.solveType(fi.getType().getJavaName());
			}

			@Override
			public String getName() {
				return fi.getName();
			}
		}
	}

	private static class AsmResolvedReferenceType extends ResolvedReferenceType {
		TypeSolver typeSolver;
		Type type;

		public AsmResolvedReferenceType(TypeSolver typeSolver, Type type, ResolvedReferenceTypeDeclaration rrtd) {
			super(rrtd);
			this.typeSolver = typeSolver;
			this.type = type;
		}

		@Override
		public String describe() {
			return type.toString();
		}

		@Override
		public ResolvedType transformTypeParameters(ResolvedTypeTransformer transformer) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isAssignableBy(ResolvedType other) {
			throw new UnsupportedOperationException();
			//return false;
		}

		@Override
		public List<ResolvedReferenceType> getAllAncestors() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<ResolvedReferenceType> getDirectAncestors() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<MethodUsage> getDeclaredMethods() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<ResolvedFieldDeclaration> getDeclaredFields() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ResolvedType toRawType() {
			throw new UnsupportedOperationException();
		}

		@Override
		protected ResolvedReferenceType create(ResolvedReferenceTypeDeclaration typeDeclaration, List<ResolvedType> typeParameters) {
			throw new UnsupportedOperationException();
		}

		@Override
		protected ResolvedReferenceType create(ResolvedReferenceTypeDeclaration typeDeclaration) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ResolvedReferenceType deriveTypeParameters(ResolvedTypeParametersMap typeParametersMap) {
			throw new UnsupportedOperationException();
		}
	}
}
