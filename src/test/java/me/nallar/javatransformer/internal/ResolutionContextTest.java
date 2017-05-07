package me.nallar.javatransformer.internal;

import me.nallar.javatransformer.api.TransformationException;
import me.nallar.javatransformer.api.Type;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class ResolutionContextTest {
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
		Type t = ResolutionContext.of("org.example", Collections.emptyList(), Collections.emptyList()).resolve("int");
		Assert.assertEquals(t.descriptor, "I");
		t = ResolutionContext.of("org.example", Collections.emptyList(), Collections.emptyList()).resolve("boolean");
		Assert.assertEquals(t.descriptor, "Z");
	}

	@Test
	public void testExtractPrimitiveArray() {
		Type t = ResolutionContext.of("org.example", Collections.emptyList(), Collections.emptyList()).resolve("int[]");
		Assert.assertEquals(t.descriptor, "[I");
		t = ResolutionContext.of("org.example", Collections.emptyList(), Collections.emptyList()).resolve("int[][]");
		Assert.assertEquals(t.descriptor, "[[I");
		t = ResolutionContext.of("org.example", Collections.emptyList(), Collections.emptyList()).resolve("boolean[]");
		Assert.assertEquals(t.descriptor, "[Z");
		t = ResolutionContext.of("org.example", Collections.emptyList(), Collections.emptyList()).resolve("boolean[][]");
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
}
