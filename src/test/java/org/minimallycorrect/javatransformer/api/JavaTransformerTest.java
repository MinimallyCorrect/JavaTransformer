package org.minimallycorrect.javatransformer.api;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import lombok.val;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.tree.ClassNode;

import com.github.javaparser.JavaParser;

import org.minimallycorrect.javatransformer.api.code.CodeFragment;

@RunWith(Parameterized.class)
public class JavaTransformerTest {
	private static final int EXPECTED_METHOD_CALL_COUNT = 4;
	private static final String[] extensions = new String[]{"java", "class"};
	private final Path input;
	private final List<Path> extraPaths;
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	public JavaTransformerTest(List<Path> inputs) {
		inputs = new ArrayList<>(inputs);
		this.input = inputs.remove(0);
		this.extraPaths = inputs;
	}

	@Parameterized.Parameters
	public static Collection<List<Path>> paths() {
		return Arrays.asList(getClassPath(), getSourcePath(), getMixPath());
	}

	private static List<Path> getClassPath() {
		return new ArrayList<>(Collections.singletonList(JavaTransformer.pathFromClass(JavaTransformerTest.class)));
	}

	private static List<Path> getSourcePath() {
		return new ArrayList<>(Arrays.asList(Paths.get("src/test/java/"), Paths.get("src/main/java")));
	}

	private static List<Path> getMixPath() {
		return new ArrayList<>(Arrays.asList(Paths.get("src/test/java/"), JavaTransformer.pathFromClass((JavaTransformer.class))));
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

	@SuppressWarnings("ResultOfMethodCallIgnored")
	@Test
	public void testTransform() throws Exception {
		Path output = folder.newFolder("output").toPath();

		JavaTransformer transformer = new JavaTransformer();
		transformer.getClassPath().addPaths(extraPaths);
		transformer.getClassPath().addPaths(Arrays.asList(JavaTransformer.pathFromClass(Assert.class), JavaTransformer.pathFromClass(ClassNode.class), JavaTransformer.pathFromClass(JavaParser.class)));

		val targetMethod = "testMethodCallExpression";
		val targetClass = this.getClass().getName();
		AtomicBoolean hasProcessedTargetClass = new AtomicBoolean(false);
		AtomicBoolean hasProcessedTargetMethod = new AtomicBoolean(false);

		transformer.addTransformer(c -> {
			System.out.println("Transforming class: " + c.getName() + " of type " + c.getClass().getSimpleName());
			val isTarget = c.getName().equals(targetClass);
			if (isTarget)
				hasProcessedTargetClass.set(true);

			c.accessFlags(it -> it.makeAccessible(true));
			c.getAnnotations().forEach(it -> {
				if (it.getType().getClassName().equals(AnnotationWithDefault.class.getName())) {
					val contract = it.toInstance(AnnotationWithDefault.class);
					Assert.assertEquals(TestEnum.SECOND, contract.testEnum());
				}
			});
			val fields = c.getFields().collect(Collectors.toList());
			val interfaceTypes = c.getInterfaceTypes();
			c.getMembers().collect(Collectors.toList());
			c.getConstructors().collect(Collectors.toList());
			c.getMethods().forEach(it -> {
				val rt = it.getReturnType();
				Assert.assertNotNull(it + " should have a return type", rt);

				val cf = it.getCodeFragment();

				if (!c.getInterfaceTypes().contains(Type.ANNOTATION)) {
					Assert.assertNotNull(it + " should have a CodeFragment", cf);
				}

				if (it.getName().equals(targetMethod)) {
					hasProcessedTargetMethod.set(true);
					val methodCalls = cf.findFragments(CodeFragment.MethodCall.class);
					Assert.assertEquals(EXPECTED_METHOD_CALL_COUNT, methodCalls.size());
					for (int i = 1; i <= EXPECTED_METHOD_CALL_COUNT; i++) {
						val call = methodCalls.get(i - 1);
						Assert.assertEquals(PrintStream.class.getName(), call.getContainingClassType().getClassName());
						val inputTypes = call.getInputTypes();
						Assert.assertNotNull("Should find inputTypes for method call expression", inputTypes);
						Assert.assertEquals(2, inputTypes.size());
						Assert.assertEquals(PrintStream.class.getName(), inputTypes.get(0).type.getClassName());
						Assert.assertEquals(String.valueOf(i), inputTypes.get(1).constantValue);
					}
				} else if (cf != null) {
					val methodCalls = cf.findFragments(CodeFragment.MethodCall.class);
					for (val call : methodCalls) {
						Assert.assertNotNull(call.getContainingClassType());
						val inputTypes = call.getInputTypes();
						Assert.assertNotNull("Should find inputTypes for method call expression", inputTypes);
					}

					cf.insert(cf, CodeFragment.InsertionPosition.OVERWRITE);
				}
			});

			if (isTarget) {
				Assert.assertEquals(5, fields.size());
				val field = fields.get(0);
				Assert.assertEquals("EXPECTED_METHOD_CALL_COUNT", field.getName());
				Assert.assertEquals("int", field.getType().getJavaName());
				Assert.assertTrue(interfaceTypes.isEmpty());
			}
		});

		System.out.println("Transforming path '" + input + "' to '" + output + "'");
		transformer.transform(input, output);

		Assert.assertTrue("Transformer must process " + targetClass, hasProcessedTargetClass.get());
		Assert.assertTrue("Transformer must process " + targetMethod, hasProcessedTargetMethod.get());

		Assert.assertTrue(exists(output.resolve("org/minimallycorrect/javatransformer/api/JavaTransformerTest.")));
	}
}
