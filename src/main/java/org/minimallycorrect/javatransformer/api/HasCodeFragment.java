package org.minimallycorrect.javatransformer.api;

import lombok.val;
import org.jetbrains.annotations.Nullable;
import org.minimallycorrect.javatransformer.api.code.CodeFragment;

import java.util.*;

public interface HasCodeFragment {
	@Nullable
	default CodeFragment getCodeFragment() {
		return null;
	}

	default <T extends CodeFragment> Iterable<T> findFragments(Class<T> fragmentType) {
		val fragment = getCodeFragment();
		return fragment == null ? Collections.emptyList() : fragment.findFragments(fragmentType);
	}
}
