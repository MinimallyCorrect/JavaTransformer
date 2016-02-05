package me.nallar.javatransformer.internal;

import me.nallar.javatransformer.api.Parameter;
import me.nallar.javatransformer.api.Type;
import org.junit.Assert;

public class MethodDescriptorTest {
	@org.junit.Test
	public void testGetReturnType() throws Exception {
		MethodDescriptor d = new MethodDescriptor("()Ljava/lang/String;", null);
		Type t = d.getReturnType();

		Assert.assertEquals("Ljava/lang/String;", t.descriptor);
		Assert.assertEquals(null, t.signature);
		Assert.assertEquals("java.lang.String", t.getClassName());
	}

	@org.junit.Test
	public void testParameters() throws Exception {
		MethodDescriptor d = new MethodDescriptor("(Ljava/lang/Object;Ljava/util/ArrayList;)Ljava/lang/String;", "(TT;TA;)Ljava/lang/String;");
		Parameter first = d.getParameters().get(0);
		Parameter second = d.getParameters().get(1);

		Assert.assertEquals("java.lang.Object", first.getClassName());
		Assert.assertEquals("T", first.getTypeParameterName());

		Assert.assertEquals("java.util.ArrayList", second.getClassName());
		Assert.assertEquals("A", second.getTypeParameterName());
	}
}
