package me.nallar.javatransformer.api;

import lombok.val;
import me.nallar.javatransformer.internal.SimpleMethodInfo;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertTrue;

public class MethodInfoTest {
	@Test
	public void testSimilar() throws Exception {
		val a = SimpleMethodInfo.of(new AccessFlags(AccessFlags.ACC_PUBLIC), Collections.emptyList(), Type.of("java.lang.String"), "name", new ArrayList<>());
		val b = SimpleMethodInfo.of(new AccessFlags(AccessFlags.ACC_PUBLIC), Collections.emptyList(), Type.of("java.lang.String"), "name", new ArrayList<>());
		assertTrue(a.similar(b));
	}
}
