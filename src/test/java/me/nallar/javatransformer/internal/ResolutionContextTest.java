package me.nallar.javatransformer.internal;

import org.junit.Assert;
import org.junit.Test;

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
}
