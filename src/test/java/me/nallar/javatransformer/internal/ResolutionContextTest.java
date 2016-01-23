package me.nallar.javatransformer.internal;

import org.junit.Assert;
import org.junit.Test;

public class ResolutionContextTest {
	@Test
	public void testClassNameToDescriptor() throws Exception {
		testClassNameToDescriptor("java.lang.String", "Ljava/lang/String;");
		testClassNameToDescriptor("nallar.test.Outer", "Lnallar/test/Outer;");
		testClassNameToDescriptor("nallar.test.Outer.Inner", "Lnallar/test/Outer$Inner;");
		testClassNameToDescriptor("BadNamingScheme.test.Outer", "LBadNamingScheme/test/Outer;");
		testClassNameToDescriptor("BadNamingScheme.test.Outer.Inner", "LBadNamingScheme/test/Outer$Inner;");
	}

	private void testClassNameToDescriptor(String in, String expected) {
		Assert.assertEquals(expected, ResolutionContext.classNameToDescriptor(in));
	}
}
