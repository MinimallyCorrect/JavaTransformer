package org.minimallycorrect.javatransformer.internal.util;

import java.io.InputStream;
import java.util.Arrays;

import lombok.SneakyThrows;

public final class StreamUtil {
	@SneakyThrows
	public static byte[] readFully(InputStream is) {
		byte[] output = {};
		int position = 0;
		while (true) {
			int bytesToRead;
			if (position >= output.length) {
				bytesToRead = output.length + 4096;
				if (output.length < position + bytesToRead) {
					output = Arrays.copyOf(output, position + bytesToRead);
				}
			} else {
				bytesToRead = output.length - position;
			}
			int bytesRead = is.read(output, position, bytesToRead);
			if (bytesRead < 0) {
				if (output.length != position) {
					output = Arrays.copyOf(output, position);
				}
				break;
			}
			position += bytesRead;
		}
		return output;
	}
}
