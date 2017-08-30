package org.minimallycorrect.javatransformer.api;

import lombok.val;
import org.junit.Assert;
import org.junit.Test;
import org.minimallycorrect.javatransformer.api.code.CodeFragment;
import org.minimallycorrect.javatransformer.internal.ByteCodeInfo;
import org.minimallycorrect.javatransformer.internal.asm.DebugPrinter;
import org.minimallycorrect.javatransformer.transform.CodeFragmentTesting;
import org.omg.CORBA.BooleanHolder;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.minimallycorrect.javatransformer.api.code.IntermediateValue.LocationType.LOCAL;
import static org.minimallycorrect.javatransformer.api.code.IntermediateValue.LocationType.STACK;

public class JavaTransformerRuntimeTest {
	private static final List<String> EXPECTED_METHOD_CALL_INPUTS = Arrays.asList("1", "2", "3", "4");
	private static final int EXPECTED_METHOD_CALL_COUNT = 4;

	@Test
	public void testTransformRuntime() throws Exception {
		final Path input = JavaTransformer.pathFromClass(JavaTransformerTest.class);
		final String name = "org.minimallycorrect.javatransformer.transform.CodeFragmentTesting";
		JavaTransformer transformer = new JavaTransformer();

		val targetClass = this.getClass().getName();
		BooleanHolder holder = new BooleanHolder(false);

		transformer.addTransformer(name, c -> {
			Assert.assertEquals(name, c.getName());
			holder.value = true;
			c.accessFlags(it -> it.makeAccessible(true));
			c.getAnnotations();
			c.getFields();
			c.getInterfaceTypes();
			c.getMembers();
			c.getConstructors();
			c.getMethods().forEach(it -> {
				val cf = it.getCodeFragment();
				Assert.assertNotNull(it + " should have a CodeFragment", cf);
				if (it.getName().equals("testMethodCallExpression")) {
					val methodCalls = cf.findFragments(CodeFragment.MethodCall.class);
					Assert.assertEquals(EXPECTED_METHOD_CALL_COUNT, methodCalls.size());
					for (int i = 1; i <= EXPECTED_METHOD_CALL_COUNT; i++) {
						System.out.println("call " + i);
						val call = methodCalls.get(i - 1);
						Assert.assertEquals("println", call.getName());
						Assert.assertEquals(PrintStream.class.getName(), call.getContainingClassType().getClassName());
						val inputTypes = call.getInputTypes();
						for (val inputType : inputTypes)
							Assert.assertEquals(STACK, inputType.location.type);
						Assert.assertNotNull("Should find inputTypes for method call expression", inputTypes);
						Assert.assertEquals(2, inputTypes.size());
						Assert.assertEquals(String.valueOf(i), inputTypes.get(1).constantValue);
						Assert.assertEquals("java.io.PrintStream", inputTypes.get(0).type.getClassName());

						val callbackCaller = c.getMethods().filter(method -> method.getName().equals("callbackCaller")).findFirst().get();
						val callbackCallerFragment = callbackCaller.getCodeFragment();

						call.insert(callbackCallerFragment, CodeFragment.InsertionPosition.OVERWRITE);
						for (val inputType : callbackCallerFragment.getInputTypes())
							Assert.assertEquals(LOCAL, inputType.location.type);
						DebugPrinter.printByteCode(((ByteCodeInfo.MethodNodeInfo) it).node, "after insert callbackCallerFragment");
					}
				}
			});
		});

		System.out.println("Transforming path '" + input + '\'');
		transformer.load(input);
		val clazz = transformer.defineClass(this.getClass().getClassLoader(), name);
		Assert.assertEquals(name, clazz.getName());
		Assert.assertTrue("Transformer must process " + targetClass, holder.value);

		val list = new ArrayList<String>();
		val codeFragmentTesting = new CodeFragmentTesting(list::add);
		codeFragmentTesting.testMethodCallExpression();
		Assert.assertEquals(EXPECTED_METHOD_CALL_INPUTS, list);
	}

}
