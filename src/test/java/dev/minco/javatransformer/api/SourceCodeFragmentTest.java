package dev.minco.javatransformer.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import lombok.SneakyThrows;
import lombok.val;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.minco.javatransformer.api.code.CodeFragment;

public class SourceCodeFragmentTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@SneakyThrows
	@Test
	public void TestThing() {
		val input = Paths.get("src/test/java/");
		val output = folder.newFolder("output").toPath();
		val jt = new JavaTransformer();
		jt.getClassPath().addPath(input);
		val hasRan = new AtomicBoolean();
		val clazz = "dev.minco.javatransformer.transform.codefragments.InsertBeforeTest";
		jt.addTransformer(clazz, (cI) -> {
			List<MethodInfo> methods = cI.getMethods().collect(Collectors.toList());
			MethodInfo testMethod = methods.stream().filter((MethodInfo it) -> it.getName().equals("testMethod")).findFirst().get();
			MethodInfo toInsert = methods.stream().filter((MethodInfo it) -> it.getName().equals("toInsert")).findFirst().get();
			testMethod.getCodeFragment().insert(toInsert.getCodeFragment(), CodeFragment.InsertionPosition.BEFORE);
			hasRan.set(true);
		});
		jt.transform(input, output);
		Assert.assertTrue("Must have transformed class", hasRan.get());
		assertFilesEqual(input.resolve(clazz.replace(".", "/") + ".java_patched"),
			output.resolve(clazz.replace(".", "/") + ".java"));
	}

	@SneakyThrows
	private void assertFilesEqual(Path expect, Path actual) {
		String expectStr = new String(Files.readAllBytes(expect))
			.replaceAll("[ \t]+", " ")
			.replace("\r", "");
		String actualStr = new String(Files.readAllBytes(actual)).replaceAll("[ \t]+", " ")
			.replace("\r", "");

		Assert.assertEquals(expectStr, actualStr);
	}
}
