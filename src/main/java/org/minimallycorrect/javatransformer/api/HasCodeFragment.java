package org.minimallycorrect.javatransformer.api;

import java.util.Collections;

import lombok.val;

import org.jetbrains.annotations.Nullable;

import org.minimallycorrect.javatransformer.api.code.CodeFragment;

public interface HasCodeFragment {
	@Nullable
	default CodeFragment.Body getCodeFragment() {
		return null;
	}

	default <T extends CodeFragment> Iterable<T> findFragments(Class<T> fragmentType) {
		val fragment = getCodeFragment();
		return fragment == null ? Collections.emptyList() : fragment.findFragments(fragmentType);
	}
}
