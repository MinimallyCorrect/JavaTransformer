package org.minimallycorrect.javatransformer.api;

import lombok.val;
import org.junit.Assert;
import org.junit.Test;

public class AnnotationTest {
	@Test
	public void testAnnotationConversion() {
		val override = Annotation.of(Type.of("java.lang.Override")).toInstance(Override.class);
		Assert.assertEquals(Override.class, override.annotationType());
		val suppressWarning = Annotation.of(Type.of("java.lang.SuppressWarnings"), new String[] { "unchecked" }).toInstance(SuppressWarnings.class);
		Assert.assertArrayEquals(new String[] { "unchecked" }, suppressWarning.value());
	}
}
