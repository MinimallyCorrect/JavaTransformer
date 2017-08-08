package org.minimallycorrect.javatransformer.internal;

import lombok.val;
import org.junit.Assert;
import org.junit.Test;
import org.minimallycorrect.javatransformer.api.AccessFlags;
import org.minimallycorrect.javatransformer.api.MethodInfo;
import org.minimallycorrect.javatransformer.api.Parameter;
import org.minimallycorrect.javatransformer.api.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class MethodNodeInfoTest {
	@Test
	public void testWrap() throws Exception {
		MethodNode node = new MethodNode();
		node.access = 1;
		node.name = "test";
		node.desc = "()Ljava/lang/String;";

		ByteCodeInfo b = new ByteCodeInfo(null, "java.lang.String", new HashMap<>());
		MethodInfo info = b.wrap(node);

		Assert.assertEquals("test", info.getName());
		Assert.assertEquals("java.lang.String", info.getReturnType().getClassName());
		Assert.assertEquals(AccessFlags.ACC_PUBLIC, info.getAccessFlags().access);

		info.setReturnType(new Type("Ljava/lang/Boolean;"));
		Assert.assertEquals("()Ljava/lang/Boolean;", node.desc);

		val parameters = info.getParameters();
		parameters.add(Parameter.of(new Type("Ljava/lang/String;", null), "test", null));
		info.setParameters(parameters);

		Assert.assertEquals("(Ljava/lang/String;)Ljava/lang/Boolean;", ((ByteCodeInfo.MethodNodeInfo) info).getDescriptor());
	}
}
