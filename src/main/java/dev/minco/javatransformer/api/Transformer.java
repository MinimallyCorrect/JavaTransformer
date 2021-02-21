package dev.minco.javatransformer.api;

import java.util.Collection;

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
