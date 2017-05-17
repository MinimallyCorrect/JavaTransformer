package org.minimallycorrect.javatransformer.api;

import lombok.val;
import org.junit.Assert;
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

	@Test
	public void testOf() throws Exception {
		testOf("java.lang.String", "Ljava/lang/String;");
		testOf("nallar.test.Outer", "Lnallar/test/Outer;");
		testOf("nallar.test.Outer.Inner", "Lnallar/test/Outer$Inner;");
		testOf("BadNamingScheme.test.Outer", "LBadNamingScheme/test/Outer;");
		testOf("BadNamingScheme.test.Outer.Inner", "LBadNamingScheme/test/Outer$Inner;");
		testOf("BadNamingScheme.Test.Outer.Inner", "LBadNamingScheme$Test$Outer$Inner;");
	}

	@Test
	public void testWithGenericType() {
		val arrayDequeCallable = Type.of("java.util.ArrayDeque").withTypeArgument(Type.of("java.util.Callable"));
		assertEquals("java.util.ArrayDeque", arrayDequeCallable.getClassName());
		assertEquals("java.util.Callable", arrayDequeCallable.getTypeArguments().get(0).getClassName());
	}

	private void testOf(String in, String expected) {
		Assert.assertEquals(expected, Type.of(in).descriptor);
	}
}
