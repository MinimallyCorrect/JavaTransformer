package org.minimallycorrect.javatransformer.internal;

import com.github.javaparser.ast.type.TypeParameter;
import lombok.val;
import org.junit.Assert;
import org.junit.Test;
import org.minimallycorrect.javatransformer.api.TransformationException;
import org.minimallycorrect.javatransformer.api.Type;

import java.util.*;

public class ResolutionContextTest {
	private ResolutionContext context() {
		return ResolutionContext.of("org.example", Collections.emptyList(), Arrays.asList(new TypeParameter("A"), new TypeParameter("B")));
	}

	@Test
	public void testExtractReal() throws Exception {
		Assert.assertEquals("ab", ResolutionContext.extractReal("ab<bc>"));
	}

	@Test
	public void testExtractGeneric() throws Exception {
		Assert.assertEquals("test", ResolutionContext.extractGeneric("test<test>"));
		Assert.assertEquals(null, ResolutionContext.extractGeneric("test"));
	}

	@Test(expected = RuntimeException.class)
	public void testExtractGenericMismatched() throws Exception {
		ResolutionContext.extractGeneric("te>st<");
		ResolutionContext.extractGeneric("test<");
		ResolutionContext.extractGeneric("te>st");
	}

	@Test
	public void testExtractPrimitive() {
		Type t = context().resolve("int");
		Assert.assertEquals(t.descriptor, "I");
		t = context().resolve("boolean");
		Assert.assertEquals(t.descriptor, "Z");
	}

	@Test
	public void testExtractPrimitiveArray() {
		Type t = context().resolve("int[]");
		Assert.assertEquals(t.descriptor, "[I");
		t = context().resolve("int[][]");
		Assert.assertEquals(t.descriptor, "[[I");
		t = context().resolve("boolean[]");
		Assert.assertEquals(t.descriptor, "[Z");
		t = context().resolve("boolean[][]");
		Assert.assertEquals(t.descriptor, "[[Z");
	}

	@Test(expected = TransformationException.class)
	public void testSanityCheckExpectedFailure() {
		ResolutionContext.sanityCheck(new Type("LDefaultPackageNotAllowed;"));
	}

	@Test
	public void testSanityCheckExpectedSuccess() {
		ResolutionContext.sanityCheck(new Type("Z"));
		ResolutionContext.sanityCheck(new Type("Ljava/lang/Object;", "TT;"));
	}

	@Test
	public void testMapWithArrayValue() {
		Type t = context().resolve("java.util.Hashtable<Integer, long[]>");
		Assert.assertEquals("java.util.Hashtable", t.getClassName());
		Assert.assertEquals("java.lang.Integer", t.getTypeArguments().get(0).getClassName());
		Assert.assertEquals("long", t.getTypeArguments().get(1).getArrayContainedType().getPrimitiveTypeName());
		Assert.assertEquals("long[]", t.getTypeArguments().get(1).getJavaName());
	}

	@Test
	public void testTypeToJavaParsetType() {
		val qualifiedType = "java.util.Hashtable<java.lang.Integer,long[]>";
		Type t = context().resolve(qualifiedType);
		val javaParserType = ResolutionContext.typeToJavaParserType(t);
		Assert.assertEquals(qualifiedType, javaParserType.asString());
	}

	@Test
	public void testSingleLengthTypeArgument() {
		Type t = context().resolve("java.util.Hashtable<A, B[]>");
		Assert.assertEquals("A", t.getTypeArguments().get(0).getTypeParameterName());
		Assert.assertEquals("B", t.getTypeArguments().get(1).getTypeParameterName());
	}
}
