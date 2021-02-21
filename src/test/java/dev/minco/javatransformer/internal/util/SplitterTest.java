package dev.minco.javatransformer.internal.util;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

public class SplitterTest {
	@Test
	public void testSplitter() throws Exception {
		testSplitter('.', "a.b.c.d", "a", "b", "c", "d");
		testSplitter('.', "a.b..c.d", "a", "b", "c", "d");
		testSplitter('.', "a.b...c..d", "a", "b", "c", "d");
		testSplitter('.', ".a.b...c..d", "a", "b", "c", "d");
		testSplitter('.', "a.b...c..d.", "a", "b", "c", "d");
		testSplitter('.', ".a.b...c..d.", "a", "b", "c", "d");
	}

	private void testSplitter(char on, String input, String... expected) {
		Assert.assertArrayEquals(expected, toArray(Splitter.on(on).splitIterable(input)));
	}

	private String[] toArray(Iterable<String> split) {
		ArrayList<String> list = new ArrayList<>();

		split.forEach(list::add);

		return list.toArray(new String[0]);
	}
}
