package org.minimallycorrect.javatransformer.api;

import java.util.List;

public interface HasTypeVariable {
	List<TypeVariable> getTypeVariables();

	void setTypeVariables(List<TypeVariable> typeVariables);
}
