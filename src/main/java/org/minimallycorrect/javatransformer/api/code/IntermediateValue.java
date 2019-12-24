package org.minimallycorrect.javatransformer.api.code;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.With;

import org.jetbrains.annotations.Nullable;

import org.minimallycorrect.javatransformer.api.Type;

@RequiredArgsConstructor
@ToString
@With
public final class IntermediateValue {
	public final static UNKNOWN_CONSTANT UNKNOWN = new UNKNOWN_CONSTANT();
	@NonNull
	public final Type type;
	/**
	 * null indicates a constant value of null, not an unknown value
	 * <p>
	 * an unknown value is represented by UNKNOWN_CONSTANT
	 *
	 * @see UNKNOWN_CONSTANT
	 */
	@Nullable
	public final Object constantValue;

	public final Location location;

	public enum LocationType {
		STACK,
		LOCAL
	}

	@AllArgsConstructor
	@ToString
	public static class Location {
		@NonNull
		public final LocationType type;
		public final int index;
		@Nullable
		public final String name;
	}

	public final static class UNKNOWN_CONSTANT {
		UNKNOWN_CONSTANT() {}

		@Override
		public String toString() {
			return "Unknown or non-constant value";
		}
	}
}
