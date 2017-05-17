package org.minimallycorrect.javatransformer.internal;

import org.minimallycorrect.javatransformer.api.ClassInfo;
import org.minimallycorrect.javatransformer.api.ClassMember;
import org.minimallycorrect.javatransformer.api.FieldInfo;
import org.minimallycorrect.javatransformer.api.MethodInfo;

import java.util.*;
import java.util.stream.*;

public interface ClassInfoStreams extends ClassInfo {
	Stream<FieldInfo> getFieldStream();

	Stream<MethodInfo> getMethodStream();

	default List<FieldInfo> getFields() {
		return getFieldStream().collect(Collectors.toCollection(ArrayList::new));
	}

	default List<MethodInfo> getMethods() {
		return getMethodStream().collect(Collectors.toCollection(ArrayList::new));
	}

	default List<ClassMember> getMembers() {
		return Stream.concat(getFieldStream(), getMethodStream()).collect(Collectors.toList());
	}
}
