package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.Type;
import org.tudalgo.algoutils.student.annotation.ForceSignature;
import org.tudalgo.algoutils.transform.SubmissionExecutionHandler;
import org.tudalgo.algoutils.transform.util.headers.ClassHeader;
import org.tudalgo.algoutils.transform.util.headers.FieldHeader;
import org.tudalgo.algoutils.transform.util.headers.Header;
import org.tudalgo.algoutils.transform.util.headers.MethodHeader;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_FINAL;

public final class Constants {

    // Types

    public static final Type OBJECT_TYPE = Type.getType(Object.class);
    public static final Type OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
    public static final Type STRING_TYPE = Type.getType(String.class);
    public static final Type STRING_ARRAY_TYPE = Type.getType(String[].class);
    public static final Type SET_TYPE = Type.getType(Set.class);
    public static final Type LIST_TYPE = Type.getType(List.class);
    public static final Type MAP_TYPE = Type.getType(Map.class);

    public static final Type HEADER_TYPE = Type.getType(Header.class);
    public static final Type CLASS_HEADER_TYPE = Type.getType(ClassHeader.class);
    public static final Type FIELD_HEADER_TYPE = Type.getType(FieldHeader.class);
    public static final Type METHOD_HEADER_TYPE = Type.getType(MethodHeader.class);

    public static final Type ENUM_CONSTANT_TYPE = Type.getType(EnumConstant.class);
    public static final Type FORCE_SIGNATURE_TYPE = Type.getType(ForceSignature.class);
    public static final Type INVOCATION_TYPE = Type.getType(Invocation.class);
    public static final Type METHOD_SUBSTITUTION_TYPE = Type.getType(MethodSubstitution.class);
    public static final Type METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE = Type.getType(MethodSubstitution.ConstructorInvocation.class);

    // Fields used in bytecode

    public static final FieldHeader INJECTED_ORIGINAL_STATIC_FIELD_VALUES = new FieldHeader(null,
        ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
        "originalStaticFieldValues$injected",
        MAP_TYPE.getDescriptor(),
        "L%s<%s%s>;".formatted(MAP_TYPE.getInternalName(), STRING_TYPE.getDescriptor(), OBJECT_TYPE.getDescriptor()));
    public static final FieldHeader INJECTED_ORIGINAL_ENUM_CONSTANTS = new FieldHeader(null,
        ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
        "originalEnumConstants$injected",
        LIST_TYPE.getDescriptor(),
        "L%s<%s>;".formatted(LIST_TYPE.getInternalName(), ENUM_CONSTANT_TYPE.getDescriptor()));

    // Methods used in bytecode

    public static final MethodHeader INJECTED_GET_ORIGINAL_CLASS_HEADER = new MethodHeader(null,
        ACC_PUBLIC | ACC_STATIC,
        "getOriginalClassHeader",
        Type.getMethodDescriptor(CLASS_HEADER_TYPE),
        null,
        null);
    public static final MethodHeader INJECTED_GET_ORIGINAL_FIELD_HEADERS = new MethodHeader(null,
        ACC_PUBLIC | ACC_STATIC,
        "getOriginalFieldHeaders",
        Type.getMethodDescriptor(SET_TYPE),
        "()L%s<%s>;".formatted(SET_TYPE.getInternalName(), FIELD_HEADER_TYPE.getDescriptor()),
        null);
    public static final MethodHeader INJECTED_GET_ORIGINAL_METHODS_HEADERS = new MethodHeader(null,
        ACC_PUBLIC | ACC_STATIC,
        "getOriginalMethodHeaders",
        Type.getMethodDescriptor(SET_TYPE),
        "()L%s<%s>;".formatted(SET_TYPE.getInternalName(), METHOD_HEADER_TYPE.getDescriptor()),
        null);
    public static final MethodHeader INJECTED_GET_ORIGINAL_STATIC_FIELD_VALUES = new MethodHeader(null,
        ACC_PUBLIC | ACC_STATIC,
        "getOriginalStaticFieldValues",
        Type.getMethodDescriptor(MAP_TYPE),
        "()L%s<%s%s>;".formatted(MAP_TYPE.getInternalName(), STRING_TYPE.getDescriptor(), OBJECT_TYPE.getDescriptor()),
        null);
    public static final MethodHeader INJECTED_GET_ORIGINAL_ENUM_CONSTANTS = new MethodHeader(null,
        ACC_PUBLIC | ACC_STATIC,
        "getOriginalEnumConstants",
        Type.getMethodDescriptor(LIST_TYPE),
        "()L%s<%s>;".formatted(LIST_TYPE.getInternalName(), ENUM_CONSTANT_TYPE.getDescriptor()),
        null);

    public static final MethodHeader SUBMISSION_EXECUTION_HANDLER_INTERNAL_LOG_INVOCATION;
    public static final MethodHeader SUBMISSION_EXECUTION_HANDLER_INTERNAL_ADD_INVOCATION;
    public static final MethodHeader SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBSTITUTION;
    public static final MethodHeader SUBMISSION_EXECUTION_HANDLER_INTERNAL_GET_SUBSTITUTION;
    public static final MethodHeader SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBMISSION_IMPL;

    public static final MethodHeader INVOCATION_CONSTRUCTOR;
    public static final MethodHeader INVOCATION_CONSTRUCTOR_WITH_INSTANCE;
    public static final MethodHeader INVOCATION_CONSTRUCTOR_ADD_PARAMETER;

    public static final MethodHeader METHOD_SUBSTITUTION_GET_CONSTRUCTOR_INVOCATION;
    public static final MethodHeader METHOD_SUBSTITUTION_EXECUTE;
    public static final MethodHeader METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_OWNER;
    public static final MethodHeader METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_DESCRIPTOR;
    public static final MethodHeader METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_ARGS;

    static {
        try {
            SUBMISSION_EXECUTION_HANDLER_INTERNAL_LOG_INVOCATION = new MethodHeader(SubmissionExecutionHandler.Internal.class.getDeclaredMethod("logInvocation", MethodHeader.class));
            SUBMISSION_EXECUTION_HANDLER_INTERNAL_ADD_INVOCATION = new MethodHeader(SubmissionExecutionHandler.Internal.class.getDeclaredMethod("addInvocation", MethodHeader.class, Invocation.class));
            SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBSTITUTION = new MethodHeader(SubmissionExecutionHandler.Internal.class.getDeclaredMethod("useSubstitution", MethodHeader.class));
            SUBMISSION_EXECUTION_HANDLER_INTERNAL_GET_SUBSTITUTION = new MethodHeader(SubmissionExecutionHandler.Internal.class.getDeclaredMethod("getSubstitution", MethodHeader.class));
            SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBMISSION_IMPL = new MethodHeader(SubmissionExecutionHandler.Internal.class.getDeclaredMethod("useSubmissionImpl", MethodHeader.class));

            INVOCATION_CONSTRUCTOR = new MethodHeader(Invocation.class.getDeclaredConstructor(Class.class, MethodHeader.class, StackTraceElement[].class));
            INVOCATION_CONSTRUCTOR_WITH_INSTANCE = new MethodHeader(Invocation.class.getDeclaredConstructor(Class.class, MethodHeader.class, StackTraceElement[].class, Object.class));
            INVOCATION_CONSTRUCTOR_ADD_PARAMETER = new MethodHeader(Invocation.class.getDeclaredMethod("addParameter", Object.class));

            METHOD_SUBSTITUTION_GET_CONSTRUCTOR_INVOCATION = new MethodHeader(MethodSubstitution.class.getDeclaredMethod("getConstructorInvocation"));
            METHOD_SUBSTITUTION_EXECUTE = new MethodHeader(MethodSubstitution.class.getDeclaredMethod("execute", Invocation.class));
            METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_OWNER = new MethodHeader(MethodSubstitution.ConstructorInvocation.class.getDeclaredMethod("owner"));
            METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_DESCRIPTOR = new MethodHeader(MethodSubstitution.ConstructorInvocation.class.getDeclaredMethod("descriptor"));
            METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_ARGS = new MethodHeader(MethodSubstitution.ConstructorInvocation.class.getDeclaredMethod("args"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Constants() {}
}
