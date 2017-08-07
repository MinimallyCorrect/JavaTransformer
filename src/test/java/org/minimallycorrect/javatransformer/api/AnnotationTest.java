package org.minimallycorrect.javatransformer.api;

import lombok.val;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class AnnotationTest {
	@Test
	public void testAnnotationConversion() {
		val override = Annotation.of(Type.of("java.lang.Override")).toInstance(Override.class);
		Assert.assertEquals(Override.class, override.annotationType());
		val suppressWarning = Annotation.of(Type.of("java.lang.SuppressWarnings"), new String[] { "unchecked" }).toInstance(SuppressWarnings.class);
		Assert.assertArrayEquals(new String[] { "unchecked" }, suppressWarning.value());
	}

	@Test
	public void testAnnotationConversionWithDefaultValues() {
		val annotationWithDefault = Annotation.of(Type.of("org.minimallycorrect.javatransformer.api.AnnotationWithDefault"), "changed").toInstance(AnnotationWithDefault.class);
		Assert.assertEquals(1, annotationWithDefault.index());
		Assert.assertEquals("changed", annotationWithDefault.value());
	}

	@Test
	public void testAnnotationConversionWithDefaultValuesAndUnmodifiableMap() {
		val map = new HashMap<String, Object>();
		map.put("value", "changed2");
		map.put("index", 2);
		val annotationWithDefault2 = Annotation.of(Type.of("org.minimallycorrect.javatransformer.api.AnnotationWithDefault"), Collections.unmodifiableMap(map)).toInstance(AnnotationWithDefault.class);
		Assert.assertEquals(2, annotationWithDefault2.index());
		Assert.assertEquals("changed2", annotationWithDefault2.value());
	}
}
