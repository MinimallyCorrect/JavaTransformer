package org.minimallycorrect.javatransformer.api;

import java.nio.file.Paths;
import java.util.Objects;

import lombok.val;

import org.junit.Assert;
import org.junit.Test;

public class ClassPathTest {
	@Test
	public void checkAddReturnsCorrectValue() {
		val classPath = ClassPath.of();
		Assert.assertTrue("path should be added successfully", classPath.addPath(Paths.get("test")));
		Assert.assertFalse("path should not be added successfully", classPath.addPath(Paths.get("test")));
		Assert.assertFalse("path should not be added successfully", classPath.addPath(Paths.get("./test")));
		Assert.assertFalse("path should not be added successfully", classPath.addPath(Paths.get("./asds/../test")));
	}

	@Test
	public void addAfterInitialsied() {
		val classPath = ClassPath.of();
		Assert.assertTrue("path should be added successfully", classPath.addPath(Paths.get("test")));
		// trigger initialisation
		Objects.requireNonNull(classPath.iterator());
		Assert.assertTrue("path should be added successfully", classPath.addPath(Paths.get("test2")));
	}

	@Test
	public void checkClassesInClassPath() {
		val classPath = ClassPath.of();
		boolean foundObject = false;
		for (val clazz : classPath) {
			if (clazz.getName().equals("java.lang.Object")) {
				foundObject = true;
				break;
			}
		}
		Assert.assertTrue("Should find java.lang.Object in " + classPath, foundObject);
	}
}
