package org.minimallycorrect.javatransformer.api;

public interface ClassMember extends Annotated, Accessible, Named {
	ClassInfo getClassInfo();
}
