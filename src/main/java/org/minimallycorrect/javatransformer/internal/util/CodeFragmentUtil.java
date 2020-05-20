package org.minimallycorrect.javatransformer.internal.util;

import lombok.NonNull;

import org.minimallycorrect.javatransformer.api.code.CodeFragment;

public final class CodeFragmentUtil {
	/**
	 * Validates if an attempted insert is sensible
	 *
	 * @return true if the insertion would have no effect and can be skipped
	 * @throws UnsupportedOperationException If the attempted insertion is not possible
	 */
	public static boolean validateInsert(@NonNull CodeFragment self, @NonNull CodeFragment codeFragment,
		@NonNull CodeFragment.InsertionPosition position,
		@NonNull CodeFragment.InsertionOptions insertionOptions) {
		if (self.equals(codeFragment)) {
			if (position == CodeFragment.InsertionPosition.OVERWRITE)
				return true;
			throw new UnsupportedOperationException("Can't insert a CodeFragment into itself");
		}
		return false;
	}
}
