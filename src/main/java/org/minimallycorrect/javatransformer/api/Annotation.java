package org.minimallycorrect.javatransformer.api;

import lombok.*;
import org.minimallycorrect.javatransformer.internal.util.JVMUtil;

import java.util.*;

@Data
@RequiredArgsConstructor(staticName = "of")
public class Annotation {
	@NonNull
	public final Type type;
	@NonNull
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	public final Map<String, Object> values;

	public static Annotation of(Type t, Object value) {
		val map = new HashMap<String, Object>();
		map.put("value", value);
		return of(t, map);
	}

	public static Annotation of(Type t) {
		return of(t, Collections.emptyMap());
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String key, Class<T> clazz) {
		val value = values.get(key);
		if (value == null)
			return null;
		if (clazz.isAssignableFrom(value.getClass()))
			return (T) value;
		if (clazz.isEnum()) {
			if (!(value instanceof String[]))
				throw new IllegalArgumentException("value for " + key + " is not a String[] so can't be mapped to enum. Actual type " + value.getClass().getName() + " value " + value);
			val array = ((String[]) value);
			val type = new Type(array[0]);
			if (!type.getClassName().endsWith(clazz.getName()))
				throw new IllegalArgumentException("value for " + key + " is of enum type " + type + " which does not match expected type " + clazz.getName() + " actual value " + Arrays.toString(array));
			return (T) JVMUtil.searchEnum((Class<? extends Enum<?>>) clazz, array[1]);
		}
		throw new UnsupportedOperationException("Can't convert enum value of type " + value.getClass().getName() + " value " + value + " to " + clazz.getName());
	}

	public void set(String key, Object value) {
		if (value != null) {
			val clazz = value.getClass();
			if (clazz.isEnum())
				value = new String[] { Type.of(clazz.getName()).descriptor, ((Enum) value).name()};
		}
		values.put(key, value);
	}

	public <T extends java.lang.annotation.Annotation> T toAnnotation(Class<T> clazz) {
		if (!clazz.getName().equals(type.getClassName()))
			throw new IllegalArgumentException("Type " + type + " can't be mapped to annotation class " + clazz);
		// TODO: change this to use our own proxy instead of reusing a sun.misc class
		//noinspection unchecked
		return (T) sun.reflect.annotation.AnnotationParser.annotationForMap(clazz, values);
	}
}
