package org.minimallycorrect.javatransformer.api;

import lombok.val;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.omg.CORBA.BooleanHolder;

import java.nio.file.*;
import java.util.*;

@RunWith(Parameterized.class)
public class JavaTransformerTest {
	private static final String[] extensions = new String[]{"java", "class"};
	private final Path input;
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	public JavaTransformerTest(Path input) {
		this.input = input;
	}

	@Parameterized.Parameters
	public static Collection<Path> paths() {
		return Arrays.asList(getClassPath(), getSourcePath());
	}

	private static Path getClassPath() {
		return JavaTransformer.pathFromClass(JavaTransformerTest.class);
	}

	private static Path getSourcePath() {
		return Paths.get("src/test/java/");
	}

	private static boolean exists(Path p) {
		Path base = p.getParent();
		String fileName = p.getFileName().toString();
		return Arrays.stream(extensions).anyMatch(it -> Files.exists(base.resolve(fileName + it)));
	}

	@Test
	public void testSkipPackageInfo() throws Exception {
		Assert.assertNull("Should skip package-info.java", new JavaTransformer().transformBytes(null, "org/example/test/package-info.java", null));
	}

	@Test
	public void testTransform() throws Exception {
		Path output = folder.newFolder("output").toPath();

		JavaTransformer transformer = new JavaTransformer();

		val targetClass = this.getClass().getName();
		BooleanHolder holder = new BooleanHolder(false);

		transformer.addTransformer(c -> {
			System.out.println("Transforming class: " + c.getName() + " of type " + c.getClass().getSimpleName());
			if (c.getName().equals(targetClass)) {
				holder.value = true;
				c.accessFlags(it -> it.makeAccessible(true));
				c.getAnnotations();
				c.getFields();
				c.getInterfaceTypes();
				c.getMembers();
			}
		});

		transformer.transform(input, output);

		Assert.assertTrue("Transformer must process " + targetClass, holder.value);

		Assert.assertTrue(exists(output.resolve("org/minimallycorrect/javatransformer/api/JavaTransformerTest.")));
	}
}
