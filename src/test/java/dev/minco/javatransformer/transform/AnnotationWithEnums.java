package dev.minco.javatransformer.transform;

import dev.minco.javatransformer.api.AnnotationWithDefault;
import dev.minco.javatransformer.api.TestEnum;
import dev.minco.javatransformer.api.code.CodeFragment;

@AnnotationWithDefault(position = CodeFragment.InsertionPosition.AFTER, testEnum = TestEnum.SECOND)
public class AnnotationWithEnums {}
