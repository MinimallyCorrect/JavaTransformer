package org.minimallycorrect.javatransformer.internal.util;

import org.junit.Assert;
import org.junit.Test;

import com.github.javaparser.ast.expr.Name;

public class NodeUtilTest {
	@Test
	public void testQualifiedName() throws Exception {
		Assert.assertEquals("java.lang.String", NodeUtil.qualifiedName(new Name("java.lang.String")));
	}
}
