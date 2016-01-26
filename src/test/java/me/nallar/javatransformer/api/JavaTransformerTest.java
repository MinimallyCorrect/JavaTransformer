package me.nallar.javatransformer.api;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.omg.CORBA.BooleanHolder;

import java.nio.file.*;

public class JavaTransformerTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testTransform() throws Exception {
		Path output = folder.newFolder("output").toPath();

		JavaTransformer transformer = new JavaTransformer();

		BooleanHolder holder = new BooleanHolder(false);

		transformer.addTransformer(c -> {
			if (c.getName().equals(JavaTransformerTest.this.getClass().getName())) {
				holder.value = true;
			}
		});

		transformer.transform(JavaTransformer.pathFromClass(this.getClass()), output);

		Assert.assertTrue("Transformer must process " + this.getClass().getName(), holder.value);
	}
}
