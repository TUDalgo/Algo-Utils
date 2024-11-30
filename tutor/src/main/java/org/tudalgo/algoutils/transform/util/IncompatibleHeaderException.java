package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.ATHROW;

/**
 * Thrown to indicate that a class, field or method (including constructors) was declared incorrectly.
 * <p>
 *     For fields, this means that the field was declared static in the submission class while not being
 *     declared static in the solution class, or vice versa.
 * </p>
 * <p>
 *     For methods, it may indicate the same problem as for fields.
 *     It may also indicate that methods or constructors have the wrong number of parameters.
 *     Lastly, it may indicate that the return type or parameter types are incompatible, e.g.,
 *     a submission class' method returns a primitive type while the solution class' method returns
 *     a reference type.
 * </p>
 *
 * @author Daniel Mangold
 */
public class IncompatibleHeaderException extends RuntimeException {

    private final String message;
    private final Header expected;
    private final Header actual;

    /**
     * Constructs a new {@link IncompatibleHeaderException} instance.
     *
     * @param message  the exception message
     * @param expected the expected header
     * @param actual   the actual header
     */
    public IncompatibleHeaderException(String message, Header expected, Header actual) {
        super();
        this.message = message;
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public String getMessage() {
        return "%s%nExpected: %s%nActual: %s%n".formatted(message, expected, actual);
    }

    /**
     * Replicates this exception in bytecode and optionally throws it.
     * If it is not thrown, a reference to the newly created instance is located at the top
     * of the method visitor's stack upon return.
     *
     * @param mv             the method visitor to use
     * @param throwException whether the exception should be thrown
     * @return the maximum stack size used
     */
    public int replicateInBytecode(MethodVisitor mv, boolean throwException) {
        int maxStack, stackSize;

        mv.visitTypeInsn(NEW, Type.getInternalName(getClass()));
        mv.visitInsn(DUP);
        mv.visitLdcInsn(message);
        maxStack = stackSize = 3;
        maxStack = Math.max(maxStack, stackSize++ + expected.buildHeader(mv));
        maxStack = Math.max(maxStack, stackSize + actual.buildHeader(mv));
        mv.visitMethodInsn(INVOKESPECIAL,
            Type.getInternalName(getClass()),
            "<init>",
            Type.getMethodDescriptor(Type.VOID_TYPE, Constants.STRING_TYPE, Constants.HEADER_TYPE, Constants.HEADER_TYPE),
            false);
        if (throwException) {
            mv.visitInsn(ATHROW);
        }
        return maxStack;
    }
}
