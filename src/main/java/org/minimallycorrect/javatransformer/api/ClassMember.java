package org.minimallycorrect.javatransformer.api;

import org.jetbrains.annotations.Contract;

public interface ClassMember extends Annotated, Accessible, HasCodeFragment, Named {
	@Contract(pure = true)
	ClassInfo getClassInfo();
}
