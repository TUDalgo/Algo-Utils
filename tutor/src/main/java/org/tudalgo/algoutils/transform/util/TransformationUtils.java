package org.tudalgo.algoutils.transform.util;

import org.tudalgo.algoutils.transform.SolutionClassNode;
import org.tudalgo.algoutils.transform.SolutionMergingClassTransformer;
import org.tudalgo.algoutils.transform.SubmissionClassInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;

import static org.objectweb.asm.Opcodes.*;

/**
 * A collection of utility methods useful for bytecode transformations.
 * @author Daniel Mangold
 */
public final class TransformationUtils {

    private TransformationUtils() {}

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
            localsIndex += (types[i].getSort() == Type.LONG || types[i].getSort() == Type.DOUBLE) ? 2 : 1;
        }
        return localsIndex;
    }

    /**
     * Builds a class header with bytecode instructions using the given method visitor and information
     * stored in the given class header.
     * Upon return, a reference to the newly created {@link ClassHeader} object is located at
     * the top of the method visitor's stack.
     *
     * @param mv          the method visitor to use
     * @param classHeader the class header to replicate in bytecode
     * @return the maximum stack size used during the operation
     */
    public static int buildClassHeader(MethodVisitor mv, ClassHeader classHeader) {
        return buildHeader(mv, classHeader, "access", "name", "signature", "superName", "interfaces");
    }

    /**
     * Builds a field header with bytecode instructions using the given method visitor and information
     * stored in the given field header.
     * Upon return, a reference to the newly created {@link FieldHeader} object is located at
     * the top of the method visitor's stack.
     *
     * @param mv          the method visitor to use
     * @param fieldHeader the field header to replicate in bytecode
     * @return the maximum stack size used during the operation
     */
    public static int buildFieldHeader(MethodVisitor mv, FieldHeader fieldHeader) {
        return buildHeader(mv, fieldHeader, "owner", "access", "name", "descriptor", "signature");
    }

    /**
     * Builds a method header with bytecode instructions using the given method visitor and information
     * stored in the given method header.
     * Upon return, a reference to the newly created {@link MethodHeader} object is located at
     * the top of the method visitor's stack.
     *
     * @param mv           the method visitor to use
     * @param methodHeader the method header to replicate in bytecode
     * @return the maximum stack size used during the operation
     */
    public static int buildMethodHeader(MethodVisitor mv, MethodHeader methodHeader) {
        return buildHeader(mv, methodHeader, "owner", "access", "name", "descriptor", "signature", "exceptions");
    }

    /**
     * Attempts to read and process a solution class from {@code resources/classes/}.
     *
     * @param transformationContext a {@link TransformationContext} object
     * @param className             the name of the solution class
     * @return the resulting {@link SolutionClassNode} object
     */
    public static SolutionClassNode readSolutionClass(TransformationContext transformationContext, String className) {
        ClassReader solutionClassReader;
        String solutionClassFilePath = "/classes/%s.bin".formatted(className);
        try (InputStream is = SolutionMergingClassTransformer.class.getResourceAsStream(solutionClassFilePath)) {
            if (is == null) {
                throw new IOException("No such resource: " + solutionClassFilePath);
            }
            solutionClassReader = new ClassReader(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        SolutionClassNode solutionClassNode = new SolutionClassNode(transformationContext, className);
        solutionClassReader.accept(solutionClassNode, 0);
        return solutionClassNode;
    }

    /**
     * Attempts to read and process a submission class.
     *
     * @param transformationContext a {@link TransformationContext} object
     * @param className             the name of the submission class
     * @return the resulting {@link SubmissionClassInfo} object
     */
    public static SubmissionClassInfo readSubmissionClass(TransformationContext transformationContext, String className) {
        ClassReader submissionClassReader;
        String submissionClassFilePath = "/%s.class".formatted(className);
        try (InputStream is = SolutionMergingClassTransformer.class.getResourceAsStream(submissionClassFilePath)) {
            if (is == null) {
                return null;
            }
            submissionClassReader = new ClassReader(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        SubmissionClassInfo submissionClassInfo = new SubmissionClassInfo(
            transformationContext,
            submissionClassReader.getClassName(),
            new ForceSignatureAnnotationProcessor(submissionClassReader)
        );
        submissionClassReader.accept(submissionClassInfo, 0);
        return submissionClassInfo;
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
     * Replicates the given header with bytecode instructions using the supplied method visitor.
     * Upon return, a reference to the newly created header object is located at
     * the top of the method visitor's stack.
     * <br>
     * Note: The number of keys must equal the length of the array returned by
     * {@link Header#getConstructorParameterTypes()}.
     * Furthermore, the result of calling {@link Header#getValue(String)} with {@code keys[i]} must
     * be assignable to the constructor parameter type at index {@code i}.
     *
     * @param mv     the method visitor to use
     * @param header the header object to replicate in bytecode
     * @param keys   the keys to get values for
     * @return the maximum stack size used during the operation
     */
    private static int buildHeader(MethodVisitor mv, Header header, String... keys) {
        Type headerType = header.getType();
        Type[] constructorParameterTypes = header.getConstructorParameterTypes();
        int maxStack, stackSize;

        mv.visitTypeInsn(NEW, header.getType().getInternalName());
        mv.visitInsn(DUP);
        maxStack = stackSize = 2;
        for (int i = 0; i < keys.length; i++) {
            Object value = header.getValue(keys[i]);
            if (constructorParameterTypes[i].equals(Constants.STRING_ARRAY_TYPE)) {
                int stackUsed = buildArray(mv, Constants.STRING_TYPE, (Object[]) value);
                maxStack = Math.max(maxStack, stackSize++ + stackUsed);
            } else {
                if (value != null) {
                    mv.visitLdcInsn(value);
                } else {
                    mv.visitInsn(ACONST_NULL);
                }
                maxStack = Math.max(maxStack, ++stackSize);
            }
        }
        mv.visitMethodInsn(INVOKESPECIAL,
            headerType.getInternalName(),
            "<init>",
            Type.getMethodDescriptor(Type.VOID_TYPE, constructorParameterTypes),
            false);

        return maxStack;
    }
}
