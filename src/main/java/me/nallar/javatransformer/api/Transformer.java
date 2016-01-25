package me.nallar.javatransformer.api;

import java.util.*;

public interface Transformer {
	/**
	 * Determines whether a class should be transformed
	 *
	 * @param className Full class name, eg. java.lang.String
	 * @return Whether the given class should be transformed
	 */
	default boolean shouldTransform(String className) {
		return true;
	}

	/**
	 * @param editor editor instance associated with a class
	 */
	void transform(ClassInfo editor);

	interface TargetedTransformer extends Transformer {
		/**
		 * @return List of classes which this transformer will run on
		 */
		Collection<String> getTargetClasses();

		default boolean shouldTransform(String className) {
			return getTargetClasses().contains(className);
		}
	}
}
