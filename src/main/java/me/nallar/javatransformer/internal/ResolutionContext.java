package me.nallar.javatransformer.internal;

import com.github.javaparser.ast.ImportDeclaration;
import me.nallar.javatransformer.api.Type;

import java.util.*;

public class ResolutionContext {
	private final Iterable<ImportDeclaration> imports;

	public ResolutionContext(Iterable<ImportDeclaration> imports) {
		this.imports = imports;
	}

	public Type resolve(com.github.javaparser.ast.type.Type type) {
		return resolve(type.toStringWithoutComments().trim());
	}

	private String resolveGenericName(String name) {
		int bracket = name.indexOf('<');
		if (bracket != -1) {
			name = name.substring(0, bracket);
		}
	}

	private Type resolve(String name) {
		// TODO: 13/01/2016 Handle generic types (ArrayList<Type> -> resolve Type, and ArrayList.)
		Type genericType = null;

		int bracket = name.indexOf('<');
		if (bracket != -1) {
			genericType = resolve(name.substring(bracket + 1, name.lastIndexOf('>')));
			name = name.substring(0, bracket);
		}

		for (ImportDeclaration anImport : imports) {
			String importName = anImport.getName().getName();
			if (importName.endsWith(name)) {
				return new Type("L" + importName + ";", genericType);
			}
		}

		if (!name.contains(".") && !Objects.equals(System.getProperty("JarTransformer.allowDefaultPackage"), "true")) {
			throw new RuntimeException("Couldn't resolve name: " + name);
		}

		return name;
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
}
