package me.nallar.javatransformer.api;

import java.util.*;

public interface HasTypeVariable {
	List<TypeVariable> getTypeVariables();

	void setTypeVariables(List<TypeVariable> typeVariables);
}
