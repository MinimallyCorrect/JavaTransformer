package org.minimallycorrect.javatransformer.internal.util;

import com.github.javaparser.ASTHelper;
import org.junit.Assert;
import org.junit.Test;

public class NodeUtilTest {
	@Test
	public void testQualifiedName() throws Exception {
		Assert.assertEquals("java.lang.String", NodeUtil.qualifiedName(ASTHelper.createNameExpr("java.lang.String")));
	}
}
