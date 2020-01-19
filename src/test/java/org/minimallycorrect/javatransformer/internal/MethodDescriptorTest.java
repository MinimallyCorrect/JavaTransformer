package org.minimallycorrect.javatransformer.internal;

import org.junit.Assert;

import org.minimallycorrect.javatransformer.api.Parameter;
import org.minimallycorrect.javatransformer.api.Type;

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

		Assert.assertEquals("java.lang.Object", first.type.getClassName());
		Assert.assertEquals("T", first.type.getTypeParameterName());

		Assert.assertEquals("java.util.ArrayList", second.type.getClassName());
		Assert.assertEquals("A", second.type.getTypeParameterName());
	}

	@org.junit.Test
	public void testParametersWithTypeParamInSignature() throws Exception {
		MethodDescriptor d = new MethodDescriptor("(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;", "<T::Ljava/lang/annotation/Annotation;>(Ljava/lang/Class<TT;>;)TT;");
		Parameter first = d.getParameters().get(0);

		Assert.assertEquals("java.lang.Class", first.type.getClassName());
		Assert.assertEquals("T", first.type.getTypeArguments().get(0).getTypeParameterName());

		Assert.assertEquals("T", d.getReturnType().getTypeParameterName());
	}

	@org.junit.Test
	public void testParametersWithTypeParam2() throws Exception {
		/*
		org.minimallycorrect.javatransformer.api.TransformationException: Failed to parse method parameters in unmodifiableMap:
		name: unmodifiableMap
		descriptor: (Ljava/util/Map;)Ljava/util/Map;
		signature:<K:Ljava/lang/Object;V:Ljava/lang/Object;>(Ljava/util/Map<+TK;+TV;>;)Ljava/util/Map<TK;TV;>;
		 */
		MethodDescriptor d = new MethodDescriptor("(Ljava/util/Map;)Ljava/util/Map;", "<K:Ljava/lang/Object;V:Ljava/lang/Object;>(Ljava/util/Map<+TK;+TV;>;)Ljava/util/Map<TK;TV;>;");
		Parameter first = d.getParameters().get(0);

		Assert.assertEquals("java.util.Map", first.type.getClassName());
		// TODO: +TK; = ? extends K
		/*
		Assert.assertEquals("T", first.type.getTypeArguments().get(0).getTypeParameterName());
		
		Assert.assertEquals("T", d.getReturnType().getTypeParameterName());
		 */
	}
}
