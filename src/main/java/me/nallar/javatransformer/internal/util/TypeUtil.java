package me.nallar.javatransformer.internal.util;

import lombok.experimental.UtilityClass;
import lombok.val;
import me.nallar.javatransformer.api.TransformationException;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

@UtilityClass
public class TypeUtil {
	public static List<String> splitTypes(final String signature, boolean isSignature) {
		if (signature == null)
			return null;

		val types = new ArrayList<String>();
		int pos = 0;
		while (pos < signature.length()) {
			val type = readType(signature, pos, isSignature);
			pos += type.length();
			types.add(type);
		}

		return types;
	}

	public static Stream<String> readTypes(String in, boolean isSignature) {
		return CollectionUtil.stream(new Supplier<String>() {
			int pos = 0;

			@Override
			public String get() {
				if (pos < in.length()) {
					String next = readType(in, pos, isSignature);
					pos += next.length();
					return next;
				}
				return null;
			}
		});
	}

	public static String readType(String in, int pos, boolean isSignature) {
		int startPos = pos;
		char c;
		String current = "";
		String name;
		while (pos < in.length())
			switch (c = in.charAt(pos++)) {
				case 'Z':
				case 'C':
				case 'B':
				case 'S':
				case 'I':
				case 'F':
				case 'J':
				case 'D':
				case 'V':
					return current + c;

				case '[':
					current += '[';
					break;

				case 'T':
					int end = in.indexOf(';', pos);
					name = in.substring(pos, end);
					return current + 'T' + name + ';';
				case 'L':
					int start = pos;
					int genericCount = 0;
					while (pos < in.length())
						switch (in.charAt(pos++)) {
							case ';':
								if (genericCount > 0)
									break;
								name = in.substring(start, pos);
								return current + 'L' + name;
							case '<':
								if (!isSignature)
									throw new TransformationException("Illegal character '<' in descriptor: " + in);
								genericCount++;
								break;
							case '>':
								genericCount--;
								break;
						}
					break;
				default:
					throw new TransformationException("Unexpected character '" + c + "' in signature/descriptor '" + in + "' Searched section '" + in.substring(startPos, pos) + "'");
			}

		throw new StringIndexOutOfBoundsException(pos);
	}
}
