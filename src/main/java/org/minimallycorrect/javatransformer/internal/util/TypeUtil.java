package org.minimallycorrect.javatransformer.internal.util;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.minimallycorrect.javatransformer.api.TransformationException;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

@UtilityClass
public class TypeUtil {
	@Contract("null, _ -> null; !null, true -> _; !null, false -> !null")
	@Nullable
	public static List<String> splitTypes(@Nullable final String signature, boolean isSignature) {
		if (signature == null)
			return null;

		val types = new ArrayList<String>();
		int pos = 0;
		while (pos < signature.length()) {
			val type = readType(signature, pos, isSignature);
			pos += type.length();
			types.add(type);
		}

		if (isSignature && types.isEmpty())
			return null;

		return types;
	}

	public static Stream<String> readTypes(@NonNull String in, boolean isSignature) {
		return CollectionUtil.stream(new Supplier<String>() {
			int pos = 0;

			@Nullable
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
		String arrayLevel = "";
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
					return arrayLevel + c;

				case '[':
					arrayLevel += '[';
					break;

				case 'T':
					int end = in.indexOf(';', pos);
					name = in.substring(pos, end);
					return arrayLevel + 'T' + name + ';';
				case 'L':
					int start = pos;
					int genericCount = 0;
					while (pos < in.length())
						switch (in.charAt(pos++)) {
							case ';':
								if (genericCount > 0)
									break;
								name = in.substring(start, pos);
								return arrayLevel + 'L' + name;
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

		throw new StringIndexOutOfBoundsException("Reached " + pos + " in " + in);
	}
}
