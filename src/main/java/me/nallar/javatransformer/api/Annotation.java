package me.nallar.javatransformer.api;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.*;

@Data
@RequiredArgsConstructor(staticName = "of")
public class Annotation {
	@NonNull
	public final Type type;
	@NonNull
	public final Map<String, Object> values;

	public static Annotation of(Type t, Object value) {
		val map = new HashMap<String, Object>();
		map.put("value", value);
		return of(t, map);
	}

	public static Annotation of(Type t) {
		return of(t, Collections.emptyMap());
	}
}
