package org.minimallycorrect.javatransformer.api;

import java.nio.file.Paths;

import lombok.val;

import org.junit.Assert;
import org.junit.Test;

public class ClassPathTest {
	@Test
	public void checkAddReturnsCorrectValue() {
		val classPath = new ClassPath();
		Assert.assertTrue("path should be added successfully", classPath.addPath(Paths.get("test")));
		Assert.assertFalse("path should not be added successfully", classPath.addPath(Paths.get("test")));
		Assert.assertFalse("path should not be added successfully", classPath.addPath(Paths.get("./test")));
		Assert.assertFalse("path should not be added successfully", classPath.addPath(Paths.get("./asds/../test")));
	}
}
