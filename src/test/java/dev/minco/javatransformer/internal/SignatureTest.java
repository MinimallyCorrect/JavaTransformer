package dev.minco.javatransformer.internal;

import lombok.val;

import org.junit.Assert;
import org.junit.Test;

import dev.minco.javatransformer.api.TypeVariable;

public class SignatureTest {
	@Test
	public void testClassSignature() {
		val sig = "<E:Ljava/lang/Object;>Ljava/lang/Object;Ljava/util/Collection<TE;>;";
		val tvs = Signature.getTypeVariables(sig);
		Assert.assertArrayEquals(new TypeVariable[]{new TypeVariable("E")}, tvs.toArray());
	}
}
