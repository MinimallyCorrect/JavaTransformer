package org.minimallycorrect.javatransformer.api;

import lombok.val;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.*;

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
