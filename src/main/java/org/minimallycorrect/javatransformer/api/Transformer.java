package org.minimallycorrect.javatransformer.api;

import java.util.*;

public interface Transformer {
	/**
	 * @param editor editor instance associated with a class
	 */
	void transform(ClassInfo editor);

	interface TargetedTransformer extends Transformer {
		/**
		 * @return List of classes which this transformer will run on
		 */
		Collection<String> getTargetClasses();
	}
}
