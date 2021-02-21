package dev.minco.javatransformer.internal.util;

import java.lang.invoke.MethodType;

import lombok.SneakyThrows;
import lombok.val;

import org.junit.Assert;
import org.junit.Test;

public class ImplLookupTest {
	private static String toString(Object o) {
		return o.getClass().getName() + "@" + Integer.toHexString(o.hashCode());
	}

	@SneakyThrows
	@Test
	public void grandParentMethodCall() {
		val lookup = UnsafeAccess.IMPL_LOOKUP;
		val baseHandle = lookup.findSpecial(Base.class, "toString",
			MethodType.methodType(String.class),
			Sub.class);
		val objectHandle = lookup.findSpecial(Object.class, "toString",
			MethodType.methodType(String.class),
			// Must use Base.class here for this reference to call Object's toString
			Base.class);
		val sub = new Sub();
		Assert.assertEquals("Sub", sub.toString());
		Assert.assertEquals("Base", baseHandle.invoke(sub));
		Assert.assertEquals(toString(sub), objectHandle.invoke(sub));
	}

	public class Sub extends Base {
		@Override
		public String toString() {
			return "Sub";
		}
	}

	public class Base {
		@Override
		public String toString() {
			return "Base";
		}
	}
}
