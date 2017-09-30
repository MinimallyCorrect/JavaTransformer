package org.minimallycorrect.javatransformer.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.val;

import org.jetbrains.annotations.Nullable;

import org.minimallycorrect.javatransformer.internal.util.JVMUtil;

@Data
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class Annotation {
	@NonNull
	public final Type type;
	@NonNull
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	public final Map<String, Object> values;

	public static Annotation of(Type t, Map<String, Object> value) {
		return new Annotation(t, new HashMap<>(value));
	}

	public static Annotation of(Type t, Object value) {
		val map = new HashMap<String, Object>();
		map.put("value", value);
		return of(t, map);
	}

	public static Annotation of(Type t) {
		return of(t, Collections.emptyMap());
	}

	@Nullable
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
				value = new String[]{Type.of(clazz.getName()).descriptor, ((Enum) value).name()};
		}
		values.put(key, value);
	}

	@SuppressWarnings("unchecked")
	@SneakyThrows
	public <T extends java.lang.annotation.Annotation> T toInstance(Class<T> clazz) {
		if (!clazz.isAnnotation())
			throw new IllegalArgumentException("Class " + clazz.getName() + " is not an annotation");
		if (!clazz.getName().equals(type.getClassName()))
			throw new IllegalArgumentException("Type " + type + " can't be mapped to annotation class " + clazz);
		return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new AnnotationProxy(clazz));
	}

	@RequiredArgsConstructor
	private final class AnnotationProxy implements InvocationHandler, java.lang.annotation.Annotation {
		final Class<? extends java.lang.annotation.Annotation> annotationType;

		public Class<? extends java.lang.annotation.Annotation> annotationType() {
			return annotationType;
		}

		@Override
		public String toString() {
			return '@' + annotationType.toString() + '(' + values.toString() + ')';
		}

		@Override
		public boolean equals(Object other) {
			return other != null && other.getClass() == AnnotationProxy.class && toString().equals(other);
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (!Modifier.isPublic(method.getModifiers()))
				return method.invoke(this, args);
			val key = method.getName();
			val returnType = method.getReturnType();
			Object value = returnType.isPrimitive() ? values.get(key) : get(key, returnType);
			if (value == null)
				value = method.getDefaultValue();
			if (value == null)
				return method.invoke(this, args);
			return value;
		}
	}
}
