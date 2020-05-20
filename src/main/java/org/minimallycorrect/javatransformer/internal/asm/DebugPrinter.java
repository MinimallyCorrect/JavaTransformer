package org.minimallycorrect.javatransformer.internal.asm;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public final class DebugPrinter {
	public static void printByteCode(MethodNode m, String operation) {
		InsnList inList = m.instructions;
		System.out.println(m.name + " at stage " + operation + " maxLocals " + m.maxLocals + " maxStack " + m.maxStack);
		AbstractInsnNode current = m.instructions.getFirst();
		AbstractInsnNode last = m.instructions.getLast();

		Printer printer = new Textifier();
		TraceMethodVisitor mp = new TraceMethodVisitor(printer);
		while (true) {
			current.accept(mp);
			if (current == last)
				break;
			current = current.getNext();
		}

		StringWriter sw = new StringWriter();
		printer.print(new PrintWriter(sw));
		printer.getText().clear();
		System.out.println(sw);
	}
}
