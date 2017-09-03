package org.minimallycorrect.javatransformer.internal.util;

import lombok.experimental.UtilityClass;
import org.minimallycorrect.javatransformer.api.AccessFlags;
import org.minimallycorrect.javatransformer.api.TransformationException;

import java.lang.reflect.*;
import java.util.*;

@UtilityClass
public class JVMUtil {
	private static final Splitter dotSplitter = Splitter.on('.');

	public static String getDescriptor(Class<?> clazz) {
		if (clazz.isPrimitive()) {
			return descriptorToPrimitiveType(clazz.getSimpleName());
		}
		return 'L' + clazz.getCanonicalName() + ';';
	}

	public static String descriptorToPrimitiveType(String descriptor) {
		switch (descriptor) {
			case "B":
				return "byte";
			case "C":
				return "char";
			case "D":
				return "double";
			case "F":
				return "float";
			case "I":
				return "int";
			case "J":
				return "long";
			case "S":
				return "short";
			case "V":
				return "void";
			case "Z":
				return "boolean";
		}

		throw new TransformationException("Invalid descriptor: " + descriptor);
	}

	public static String primitiveTypeToDescriptor(String primitive) {
		return primitiveTypeToDescriptor(primitive, false);
	}

	public static String primitiveTypeToDescriptor(String primitive, boolean allowMissing) {
		switch (primitive) {
			case "byte":
				return "B";
			case "char":
				return "C";
			case "double":
				return "D";
			case "float":
				return "F";
			case "int":
				return "I";
			case "long":
				return "J";
			case "short":
				return "S";
			case "void":
				return "V";
			case "boolean":
				return "Z";
		}

		if (allowMissing)
			return null;

		throw new TransformationException("Invalid primitive type: " + primitive);
	}

	public static <T extends Enum<?>> T searchEnum(Class<T> enumeration, String search) {
		for (T each : enumeration.getEnumConstants()) {
			if (each.name().equalsIgnoreCase(search)) {
				return each;
			}
		}
		throw new IllegalArgumentException("Can't find enum value with name " + search + " in " + enumeration);
	}

	public static String getParameterList(Method m) {
		List<Class<?>> parameterClasses = new ArrayList<>(Arrays.asList(m.getParameterTypes()));
		StringBuilder parameters = new StringBuilder();
		for (Class<?> clazz : parameterClasses) {
			parameters.append(getDescriptor(clazz));
		}
		return parameters.toString();
	}

	public static String accessIntToString(int access) {
		StringBuilder result = new StringBuilder();

		if (hasFlag(access, AccessFlags.ACC_PUBLIC))
			result.append(" public");

		if (hasFlag(access, AccessFlags.ACC_PRIVATE))
			result.append(" private");

		if (hasFlag(access, AccessFlags.ACC_PROTECTED))
			result.append(" protected");

		if (hasFlag(access, AccessFlags.ACC_STATIC))
			result.append(" static");

		if (hasFlag(access, AccessFlags.ACC_FINAL))
			result.append(" final");

		return result.toString().trim();
	}

	public static int accessStringToInt(String access) {
		int a = 0;
		for (String accessPart : Splitter.on(' ').splitIterable(access)) {
			switch (accessPart) {
				case "public":
					a |= AccessFlags.ACC_PUBLIC;
					break;
				case "protected":
					a |= AccessFlags.ACC_PROTECTED;
					break;
				case "private":
					a |= AccessFlags.ACC_PRIVATE;
					break;
				case "static":
					a |= AccessFlags.ACC_STATIC;
					break;
				case "synthetic":
					a |= AccessFlags.ACC_SYNTHETIC;
					break;
				default:
					throw new TransformationException("Unknown access string " + access);
			}
		}
		return a;
	}

	public static String fileNameToClassName(String f) {
		f = removeFromEnd(f, ".class");
		f = removeFromEnd(f, ".java");
		return f.replace('\\', '.').replace('/', '.');
	}

	public static String classNameToFileName(String f) {
		return classNameToSlashName(f) + ".class";
	}

	public static String classNameToSlashName(String f) {
		return f.replace('.', '/');
	}

	public static String classNameToSlashName(Class<?> returnClass) {
		return classNameToSlashName(returnClass.getName());
	}

	private static String removeFromEnd(String s, String f) {
		return s.endsWith(f) ? s.substring(0, s.length() - f.length()) : s;
	}

	public static boolean hasFlag(int access, int flag) {
		return (access & flag) != 0;
	}

	public static int replaceFlag(int in, int from, int to) {
		if ((in & from) != 0) {
			in &= ~from;
			in |= to;
		}
		return in;
	}

	public static int makeAccess(int access, boolean makePublic) {
		access = makeAtLeastProtected(access);
		if (makePublic) {
			access = replaceFlag(access, AccessFlags.ACC_PROTECTED, AccessFlags.ACC_PUBLIC);
		}
		return access;
	}

	public static int makeAtLeastProtected(int access) {
		if (hasFlag(access, AccessFlags.ACC_PUBLIC) || hasFlag(access, AccessFlags.ACC_PROTECTED)) {
			// already protected or public
			return access;
		}
		if (hasFlag(access, AccessFlags.ACC_PRIVATE)) {
			// private -> protected
			return replaceFlag(access, AccessFlags.ACC_PRIVATE, AccessFlags.ACC_PROTECTED);
		}
		// not public, protected or private so must be package-local
		// change to public - protected doesn't include package-local.
		return access | AccessFlags.ACC_PUBLIC;
	}

	public static String classNameToJLSName(String className) {
		List<String> parts = new ArrayList<>();
		dotSplitter.splitIterable(className).forEach(parts::add);

		boolean possibleClass = true;
		for (int i = parts.size() - 1, size = i; i >= 0; i--) {
			String part = parts.get(i);

			boolean last = i == size;

			if (!last && !Character.isUpperCase(part.charAt(0))) {
				possibleClass = false;
			}

			if (!last) {
				parts.set(i, part + (possibleClass ? '$' : '/'));
			}
		}

		return Joiner.on().join(parts);
	}
}
