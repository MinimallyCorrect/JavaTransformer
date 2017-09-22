package org.minimallycorrect.javatransformer.api;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;

import lombok.val;

import org.junit.Test;

import org.minimallycorrect.javatransformer.internal.SimpleMethodInfo;

public class MethodInfoTest {
	@Test
	public void testSimilar() throws Exception {
		val a = SimpleMethodInfo.of(new AccessFlags(AccessFlags.ACC_PUBLIC), Collections.emptyList(), Type.of("java.lang.String"), "name", new ArrayList<>());
		val b = SimpleMethodInfo.of(new AccessFlags(AccessFlags.ACC_PUBLIC), Collections.emptyList(), Type.of("java.lang.String"), "name", new ArrayList<>());
		assertTrue(a.similar(b));
	}
}
