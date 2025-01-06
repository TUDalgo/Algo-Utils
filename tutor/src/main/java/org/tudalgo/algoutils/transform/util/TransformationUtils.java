package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.StringJoiner;

import static org.objectweb.asm.Opcodes.*;

/**
 * A collection of utility methods useful for bytecode transformations.
 * @author Daniel Mangold
 */
public final class TransformationUtils {

    private TransformationUtils() {}

    /**
     * Returns transformed modifiers, enabling easy access from tests.
     * The returned modifiers have public visibility and an unset final-flag.
     *
     * @param access the modifiers to transform
     * @return the transformed modifiers
     */
    public static int transformAccess(int access) {
        return access & ~ACC_FINAL & ~ACC_PRIVATE & ~ACC_PROTECTED | ACC_PUBLIC;
    }

    /**
     * Whether the given members have the same execution context.
     * Two members are considered to have the same execution context if they are either
     * both static or both non-static.
     * Furthermore, if they are non-static they need to be both abstract or non-abstract.
     *
     * @param access1 the modifiers of the first member
     * @param access2 the modifiers of the second member
     * @return true, if both members have the same execution context, otherwise false
     */
    public static boolean contextIsCompatible(int access1, int access2) {
        return (access1 & ACC_STATIC) == (access2 & ACC_STATIC) && (access1 & ACC_ABSTRACT) == (access2 & ACC_ABSTRACT);
    }

    /**
     * Whether the given opcode can be used on the given member.
     *
     * @param opcode the opcode to check
     * @param access the member's modifiers
     * @return true, if the opcode can be used on the member, otherwise false
     */
    public static boolean opcodeIsCompatible(int opcode, int access) {
        return (opcode == GETSTATIC || opcode == PUTSTATIC || opcode == INVOKESTATIC) == ((access & ACC_STATIC) != 0);
    }

    /**
     * Whether the given method is a lambda.
     *
     * @param access the method's modifiers
     * @param name   the method's name
     * @return true, if the method is a lambda, otherwise false
     */
    public static boolean isLambdaMethod(int access, String name) {
        return (access & ACC_SYNTHETIC) != 0 && name.startsWith("lambda$");
    }

    /**
     * Whether the given type is a
     * <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.11.1">category 2 computational type</a>.
     *
     * @param type the type to check
     * @return true, if the given type is a category 2 computational type, otherwise false
     */
    public static boolean isCategory2Type(Type type) {
        return type.getSort() == Type.LONG || type.getSort() == Type.DOUBLE;
    }

    /**
     * Calculates the true index of variables in the locals array.
     * Variables with type long or double occupy two slots in the locals array,
     * so the "expected" or "natural" index of these variables might be shifted.
     *
     * @param types the parameter types
     * @param index the "natural" index of the variable
     * @return the true index
     */
    public static int getLocalsIndex(Type[] types, int index) {
        int localsIndex = 0;
        for (int i = 0; i < index; i++) {
            localsIndex += isCategory2Type(types[i]) ? 2 : 1;
        }
        return localsIndex;
    }

    /**
     * Automatically box primitive types using the supplied {@link MethodVisitor}.
     * If the given type is not a primitive type, this method does nothing.
     *
     * @param mv   the {@link MethodVisitor} to use
     * @param type the type of the value
     */
    public static void boxType(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            case Type.BYTE -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            case Type.SHORT -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            case Type.CHAR -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            case Type.INT -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            case Type.FLOAT -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            case Type.LONG -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            case Type.DOUBLE -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        }
    }

    /**
     * Automatically unbox primitive types using the supplied {@link MethodVisitor}.
     * If the given type is not a primitive type, then this method will cast it to the specified type.
     *
     * @param mv   the {@link MethodVisitor} to use
     * @param type the type of the value
     */
    public static void unboxType(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            }
            case Type.BYTE -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
            }
            case Type.SHORT -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
            }
            case Type.CHAR -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
            }
            case Type.INT -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            }
            case Type.FLOAT -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
            }
            case Type.LONG -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            }
            case Type.DOUBLE -> {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            }
            case Type.OBJECT, Type.ARRAY -> mv.visitTypeInsn(CHECKCAST, type.getInternalName());
        }
    }

    /**
     * Places the given type's default value on top of the method visitor's stack.
     *
     * @param mv   the method visitor to use
     * @param type the type to get the default value for
     */
    public static void getDefaultValue(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> mv.visitInsn(ICONST_0);
            case Type.FLOAT -> mv.visitInsn(FCONST_0);
            case Type.LONG -> mv.visitInsn(LCONST_0);
            case Type.DOUBLE -> mv.visitInsn(DCONST_0);
            case Type.OBJECT, Type.ARRAY -> mv.visitInsn(ACONST_NULL);
        }
    }

    /**
     * Recursively replicates the given array with bytecode instructions using the supplied method visitor.
     * Upon return, a reference to the newly created array is located at
     * the top of the method visitor's stack.
     * {@code componentType} must denote a primitive type, a type compatible with the LDC instruction
     * and its variants, or an array of either.
     *
     * @param mv            the method visitor to use
     * @param componentType the array's component type
     * @param array         the array to replicate, may be null
     * @return the maximum stack size used during the operation
     */
    public static int buildArray(MethodVisitor mv, Type componentType, Object[] array) {
        int componentTypeSort = componentType.getSort();
        int maxStack, stackSize;
        if (array == null) {
            mv.visitInsn(ACONST_NULL);
            return 1;
        }

        mv.visitIntInsn(SIPUSH, array.length);
        if (componentTypeSort == Type.OBJECT || componentTypeSort == Type.ARRAY) {
            mv.visitTypeInsn(ANEWARRAY, componentType.getInternalName());
        } else {
            int operand = switch (componentTypeSort) {
                case Type.BOOLEAN -> T_BOOLEAN;
                case Type.BYTE -> T_BYTE;
                case Type.SHORT -> T_SHORT;
                case Type.CHAR -> T_CHAR;
                case Type.INT -> T_INT;
                case Type.FLOAT -> T_FLOAT;
                case Type.LONG -> T_LONG;
                case Type.DOUBLE -> T_DOUBLE;
                default -> throw new IllegalArgumentException("Unsupported component type: " + componentType);
            };
            mv.visitIntInsn(NEWARRAY, operand);
        }
        maxStack = stackSize = 1;

        for (int i = 0; i < array.length; i++, stackSize -= 3) {
            mv.visitInsn(DUP);
            mv.visitIntInsn(SIPUSH, i);
            maxStack = Math.max(maxStack, stackSize += 2);
            if (componentTypeSort == Type.ARRAY) {
                int stackUsed = buildArray(mv, Type.getType(componentType.getDescriptor().substring(1)), (Object[]) array[i]);
                maxStack = Math.max(maxStack, stackSize++ + stackUsed);
            } else {
                mv.visitLdcInsn(array[i]);
                maxStack = Math.max(maxStack, ++stackSize);
            }
            mv.visitInsn(componentType.getOpcode(IASTORE));
        }

        return maxStack;
    }

    /**
     * Returns a human-readable form of the given modifiers.
     *
     * @param modifiers the modifiers to use
     * @return a string with human-readable modifiers
     */
    public static String toHumanReadableModifiers(int modifiers) {
        Map<Integer, String> readableModifiers = Map.of(
            ACC_PUBLIC, "public",
            ACC_PRIVATE, "private",
            ACC_PROTECTED, "protected",
            ACC_STATIC, "static",
            ACC_FINAL, "final",
            ACC_INTERFACE, "interface",
            ACC_ABSTRACT, "abstract",
            ACC_SYNTHETIC, "synthetic",
            ACC_ENUM, "enum",
            ACC_RECORD, "record"
        );
        StringJoiner joiner = new StringJoiner(" ");
        for (int i = 1; i <= ACC_RECORD; i = i << 1) {
            if ((modifiers & i) != 0 && readableModifiers.containsKey(i)) {
                joiner.add(readableModifiers.get(i));
            }
        }
        return joiner.toString();
    }

    /**
     * Returns a human-readable form of the given type.
     *
     * @param type the type to use
     * @return the human-readable type
     */
    public static String toHumanReadableType(Type type) {
        return switch (type.getSort()) {
            case Type.VOID -> "void";
            case Type.BOOLEAN -> "boolean";
            case Type.BYTE -> "byte";
            case Type.SHORT -> "short";
            case Type.CHAR -> "char";
            case Type.INT -> "int";
            case Type.FLOAT -> "float";
            case Type.LONG -> "long";
            case Type.DOUBLE -> "double";
            case Type.ARRAY -> toHumanReadableType(type.getElementType()) + "[]".repeat(type.getDimensions());
            case Type.OBJECT -> type.getInternalName().replace('/', '.');
            default -> throw new IllegalStateException("Unexpected type: " + type);
        };
    }

    /**
     * Returns the class that represents the given type.
     *
     * @param type the type whose class representation to get
     * @return the class that represents the given type
     * @throws ClassNotFoundException if the class for a reference type could not be found
     */
    public static Class<?> getClassForType(Type type) throws ClassNotFoundException {
        return switch (type.getSort()) {
            case Type.VOID -> void.class;
            case Type.BOOLEAN -> boolean.class;
            case Type.BYTE -> byte.class;
            case Type.SHORT -> short.class;
            case Type.CHAR -> char.class;
            case Type.INT -> int.class;
            case Type.FLOAT -> float.class;
            case Type.LONG -> long.class;
            case Type.DOUBLE -> double.class;
            case Type.OBJECT, Type.ARRAY -> Class.forName(type.getInternalName().replace('/', '.'));
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }
}
