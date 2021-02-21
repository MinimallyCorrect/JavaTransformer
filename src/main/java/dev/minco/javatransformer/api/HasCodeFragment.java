package dev.minco.javatransformer.api;

import java.util.Collections;
import java.util.List;

import lombok.val;

import org.jetbrains.annotations.Nullable;

import dev.minco.javatransformer.api.code.CodeFragment;

public interface HasCodeFragment {
	@Nullable
	default CodeFragment.Body getCodeFragment() {
		return null;
	}

	default <T extends CodeFragment> List<T> findFragments(Class<T> fragmentType) {
		val fragment = getCodeFragment();
		return fragment == null ? Collections.emptyList() : fragment.findFragments(fragmentType);
	}
}
