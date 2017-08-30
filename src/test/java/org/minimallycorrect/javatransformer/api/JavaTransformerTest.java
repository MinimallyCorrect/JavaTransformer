package org.minimallycorrect.javatransformer.api;

import lombok.val;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.minimallycorrect.javatransformer.api.code.CodeFragment;
import org.omg.CORBA.BooleanHolder;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@RunWith(Parameterized.class)
public class JavaTransformerTest {
	private static final int EXPECTED_METHOD_CALL_COUNT = 4;
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
			if (c.getName().equals(targetClass))
				holder.value = true;

			c.accessFlags(it -> it.makeAccessible(true));
			c.getAnnotations();
			c.getFields();
			c.getInterfaceTypes();
			c.getMembers();
			c.getConstructors();
			c.getMethods().forEach(it -> {
				val cf = it.getCodeFragment();

				// TODO: remove once added for SourceCodeInfo
				if (cf == null)
					return;

				Assert.assertNotNull(it + " should have a CodeFragment", cf);
				if (it.getName().equals("testMethodCallExpression")) {
					val methodCalls = cf.findFragments(CodeFragment.MethodCall.class);
					Assert.assertEquals(EXPECTED_METHOD_CALL_COUNT, methodCalls.size());
					for (int i = 1; i <= EXPECTED_METHOD_CALL_COUNT; i++) {
						val call = methodCalls.get(i - 1);
						Assert.assertEquals(PrintStream.class.getName(), call.getContainingClassType().getClassName());
						val inputTypes = call.getInputTypes();
						Assert.assertNotNull("Should find inputTypes for method call expression", inputTypes);
						Assert.assertEquals(2, inputTypes.size());
						Assert.assertEquals(inputTypes.get(1).constantValue, String.valueOf(i));
					}
				} else {
					cf.insert(cf, CodeFragment.InsertionPosition.OVERWRITE);
				}
			});
		});

		System.out.println("Transforming path '" + input + "' to '" + output + "'");
		transformer.transform(input, output);

		Assert.assertTrue("Transformer must process " + targetClass, holder.value);

		Assert.assertTrue(exists(output.resolve("org/minimallycorrect/javatransformer/api/JavaTransformerTest.")));
	}
}
