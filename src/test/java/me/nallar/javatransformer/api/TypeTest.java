package me.nallar.javatransformer.api;

import org.junit.Test;

import static org.junit.Assert.*;

public class TypeTest {
	@Test
	public void testTypeParameter() throws Exception {
		Type typeParameter = new Type("Ljava/lang/Object;", "TT;");
		assertTrue(typeParameter.isClassType());
		assertTrue(typeParameter.isTypeParameter());
		assertFalse(typeParameter.isPrimitiveType());

		assertEquals("T", typeParameter.getTypeParameterName());
		assertEquals("java.lang.Object", typeParameter.getClassName());
	}

	@Test
	public void testClassParameter() throws Exception {
		Type typeParameter = new Type("Ljava/lang/Object;", null);
		assertTrue(typeParameter.isClassType());
		assertFalse(typeParameter.isTypeParameter());
		assertFalse(typeParameter.isPrimitiveType());

		assertEquals("java.lang.Object", typeParameter.getClassName());
	}

	@Test
	public void testPrimitiveParameter() throws Exception {
		Type typeParameter = new Type("Z", null);
		assertFalse(typeParameter.isClassType());
		assertFalse(typeParameter.isTypeParameter());
		assertTrue(typeParameter.isPrimitiveType());

		assertEquals("boolean", typeParameter.getPrimitiveTypeName());
	}
}
