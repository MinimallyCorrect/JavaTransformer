package dev.minco.javatransformer.api;

import lombok.val;

import org.junit.Assert;
import org.junit.Test;

public class TypeTest {
	@Test
	public void testTypeParameter() throws Exception {
		Type typeParameter = new Type("Ljava/lang/Object;", "TT;");
		Assert.assertTrue(typeParameter.isClassType());
		Assert.assertTrue(typeParameter.isTypeParameter());
		Assert.assertFalse(typeParameter.isPrimitiveType());

		Assert.assertEquals("T", typeParameter.getTypeParameterName());
		Assert.assertEquals("java.lang.Object", typeParameter.getClassName());
	}

	@Test
	public void testClassParameter() throws Exception {
		Type typeParameter = new Type("Ljava/lang/Object;", null);
		Assert.assertTrue(typeParameter.isClassType());
		Assert.assertFalse(typeParameter.isTypeParameter());
		Assert.assertFalse(typeParameter.isPrimitiveType());

		Assert.assertEquals("java.lang.Object", typeParameter.getClassName());
	}

	@Test
	public void testPrimitiveParameter() throws Exception {
		Type typeParameter = new Type("Z", null);
		Assert.assertFalse(typeParameter.isClassType());
		Assert.assertFalse(typeParameter.isTypeParameter());
		Assert.assertTrue(typeParameter.isPrimitiveType());

		Assert.assertEquals("boolean", typeParameter.getPrimitiveTypeName());
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
		Assert.assertEquals("java.util.ArrayDeque", arrayDequeCallable.getClassName());
		Assert.assertEquals("java.util.Callable", arrayDequeCallable.getTypeArguments().get(0).getClassName());
	}

	private void testOf(String in, String expected) {
		Assert.assertEquals(expected, Type.of(in).descriptor);
	}
}
