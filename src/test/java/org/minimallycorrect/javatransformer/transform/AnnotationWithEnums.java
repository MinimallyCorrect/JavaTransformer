package org.minimallycorrect.javatransformer.transform;

import org.minimallycorrect.javatransformer.api.AnnotationWithDefault;
import org.minimallycorrect.javatransformer.api.TestEnum;
import org.minimallycorrect.javatransformer.api.code.CodeFragment;

@AnnotationWithDefault(position = CodeFragment.InsertionPosition.AFTER, testEnum = TestEnum.SECOND)
public class AnnotationWithEnums {
}
