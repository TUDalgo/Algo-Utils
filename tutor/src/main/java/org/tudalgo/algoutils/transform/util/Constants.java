package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.Type;
import org.tudalgo.algoutils.student.annotation.ForceSignature;
import org.tudalgo.algoutils.transform.SubmissionExecutionHandler;

public final class Constants {

    // Types

    public static final Type OBJECT_TYPE = Type.getType(Object.class);
    public static final Type OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
    public static final Type STRING_TYPE = Type.getType(String.class);
    public static final Type STRING_ARRAY_TYPE = Type.getType(String[].class);

    public static final Type CLASS_HEADER_TYPE = Type.getType(ClassHeader.class);
    public static final Type[] CLASS_HEADER_CONSTRUCTOR_TYPES = new Type[] {
        Type.INT_TYPE,
        STRING_TYPE,
        STRING_TYPE,
        STRING_TYPE,
        STRING_ARRAY_TYPE
    };
    public static final Type FIELD_HEADER_TYPE = Type.getType(FieldHeader.class);
    public static final Type[] FIELD_HEADER_CONSTRUCTOR_TYPES = new Type[] {
        STRING_TYPE,
        Type.INT_TYPE,
        STRING_TYPE,
        STRING_TYPE,
        STRING_TYPE
    };
    public static final Type METHOD_HEADER_TYPE = Type.getType(MethodHeader.class);
    public static final Type[] METHOD_HEADER_CONSTRUCTOR_TYPES = new Type[] {
        STRING_TYPE,
        Type.INT_TYPE,
        STRING_TYPE,
        STRING_TYPE,
        STRING_TYPE,
        STRING_ARRAY_TYPE
    };

    public static final Type FORCE_SIGNATURE_TYPE = Type.getType(ForceSignature.class);
    public static final Type INVOCATION_TYPE = Type.getType(Invocation.class);
    public static final Type METHOD_SUBSTITUTION_TYPE = Type.getType(MethodSubstitution.class);
    public static final Type METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE = Type.getType(MethodSubstitution.ConstructorInvocation.class);
    public static final Type SUBMISSION_EXECUTION_HANDLER_TYPE = Type.getType(SubmissionExecutionHandler.class);
    public static final Type SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE = Type.getType(SubmissionExecutionHandler.Internal.class);

    private Constants() {}
}
