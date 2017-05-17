package org.minimallycorrect.javatransformer.internal.util;

import com.github.javaparser.ast.expr.Name;
import org.junit.Assert;
import org.junit.Test;

public class NodeUtilTest {
	@Test
	public void testQualifiedName() throws Exception {
		Assert.assertEquals("java.lang.String", NodeUtil.qualifiedName(new Name("java.lang.String")));
	}
}
