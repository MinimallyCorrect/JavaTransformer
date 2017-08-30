package org.minimallycorrect.javatransformer.api;

public interface ClassMember extends Annotated, Accessible, HasCodeFragment, Named {
	ClassInfo getClassInfo();
}
