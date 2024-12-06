package org.tudalgo.algoutils.transform.util.headers;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.tudalgo.algoutils.transform.util.Constants;
import org.tudalgo.algoutils.transform.util.TransformationUtils;

import java.util.Arrays;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.NEW;

/**
 * Common interface of all header records.
 */
public sealed interface Header permits ClassHeader, FieldHeader, MethodHeader {

    /**
     * Returns the type for this header.
     *
     * @return the type for this header
     */
    Type getHeaderType();

    HeaderRecordComponent[] getComponents();

    int modifiers();

    /**
     * Replicates the given header with bytecode instructions using the supplied method visitor.
     * Upon return, a reference to the newly created header object is located at
     * the top of the method visitor's stack.
     *
     * @param mv the method visitor to use
     * @return the maximum stack size used during the operation
     */
    default int buildHeader(MethodVisitor mv) {
        Type headerType = getHeaderType();
        HeaderRecordComponent[] components = getComponents();
        int maxStack, stackSize;

        mv.visitTypeInsn(NEW, getHeaderType().getInternalName());
        mv.visitInsn(DUP);
        maxStack = stackSize = 2;
        for (HeaderRecordComponent component : components) {
            Object value = component.value();
            if (component.type().equals(Constants.STRING_ARRAY_TYPE)) {
                int stackUsed = TransformationUtils.buildArray(mv, Constants.STRING_TYPE, (Object[]) value);
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
            Type.getMethodDescriptor(Type.VOID_TYPE, Arrays.stream(components).map(HeaderRecordComponent::type).toArray(Type[]::new)),
            false);

        return maxStack;
    }

    record HeaderRecordComponent(Type type, Object value) {}
}
