package org.tudalgo.algoutils.transform.methods;

import org.objectweb.asm.*;
import org.tudalgo.algoutils.transform.SubmissionClassInfo;
import org.tudalgo.algoutils.transform.util.MethodHeader;
import org.tudalgo.algoutils.transform.util.TransformationContext;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;

public abstract class BaseMethodVisitor extends MethodVisitor {

    protected final MethodVisitor delegate;
    protected final TransformationContext transformationContext;
    protected final SubmissionClassInfo submissionClassInfo;
    protected final MethodHeader originalMethodHeader;
    protected final MethodHeader computedMethodHeader;

    protected final boolean headerMismatch;
    protected final boolean isStatic;
    protected final boolean isConstructor;

    protected final int nextLocalsIndex;
    protected final List<Object> fullFrameLocals;

    protected BaseMethodVisitor(MethodVisitor delegate,
                                TransformationContext transformationContext,
                                SubmissionClassInfo submissionClassInfo,
                                MethodHeader originalMethodHeader,
                                MethodHeader computedMethodHeader) {
        super(ASM9, delegate);
        this.delegate = delegate;
        this.transformationContext = transformationContext;
        this.submissionClassInfo = submissionClassInfo;
        this.originalMethodHeader = originalMethodHeader;
        this.computedMethodHeader = computedMethodHeader;

        this.isStatic = (computedMethodHeader.access() & ACC_STATIC) != 0;
        this.isConstructor = computedMethodHeader.name().equals("<init>");

        // calculate length of locals array, including "this" if applicable
        this.nextLocalsIndex = (Type.getArgumentsAndReturnSizes(computedMethodHeader.descriptor()) >> 2) - (isStatic ? 1 : 0);

        this.fullFrameLocals = Arrays.stream(Type.getArgumentTypes(computedMethodHeader.descriptor()))
            .map(type -> switch (type.getSort()) {
                case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> INTEGER;
                case Type.FLOAT -> FLOAT;
                case Type.LONG -> LONG;
                case Type.DOUBLE -> DOUBLE;
                default -> type.getInternalName();
            })
            .collect(Collectors.toList());
        if (!isStatic) {
            this.fullFrameLocals.addFirst(isConstructor ? UNINITIALIZED_THIS : computedMethodHeader.owner());
        }

        int[] originalParameterTypes = Arrays.stream(Type.getArgumentTypes(originalMethodHeader.descriptor()))
            .mapToInt(Type::getSort)
            .toArray();
        int originalReturnType = Type.getReturnType(originalMethodHeader.descriptor()).getSort();
        int[] computedParameterTypes = Arrays.stream(Type.getArgumentTypes(computedMethodHeader.descriptor()))
            .mapToInt(Type::getSort)
            .toArray();
        int computedReturnType = Type.getReturnType(computedMethodHeader.descriptor()).getSort();
        this.headerMismatch = !(Arrays.equals(originalParameterTypes, computedParameterTypes) && originalReturnType == computedReturnType);
    }

    // Prevent bytecode to be added to the method if there is a header mismatch

    @Override
    public void visitLdcInsn(Object value) {
        if (headerMismatch) return;

        super.visitLdcInsn(value instanceof Type type ? transformationContext.toComputedType(type) : value);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (headerMismatch) return;

        super.visitTypeInsn(opcode, transformationContext.toComputedType(type).getInternalName());
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        if (!headerMismatch) {
            Object[] computedLocals = local == null ? null : Arrays.stream(local)
                .map(o -> o instanceof String s ? transformationContext.toComputedType(s).getInternalName() : o)
                .toArray();
            Object[] computedStack = stack == null ? null : Arrays.stream(stack)
                .map(o -> o instanceof String s ? transformationContext.toComputedType(s).getInternalName() : o)
                .toArray();
            super.visitFrame(type, numLocal, computedLocals, numStack, computedStack);
        }
    }

    @Override
    public void visitInsn(int opcode) {
        if (!headerMismatch) {
            super.visitInsn(opcode);
        }
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        if (!headerMismatch) {
            super.visitIntInsn(opcode, operand);
        }
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        if (!headerMismatch) {
            super.visitIincInsn(varIndex, increment);
        }
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
        if (!headerMismatch) {
            super.visitVarInsn(opcode, varIndex);
        }
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        if (!headerMismatch) {
            super.visitJumpInsn(opcode, label);
        }
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        if (headerMismatch) return;

        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        if (!headerMismatch) {
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        if (!headerMismatch) {
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        if (headerMismatch) return;

        super.visitMultiANewArrayInsn(transformationContext.toComputedType(descriptor).getDescriptor(), numDimensions);
    }

    @Override
    public void visitLabel(Label label) {
        if (!headerMismatch) {
            super.visitLabel(label);
        }
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return headerMismatch ? null : super.visitInsnAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (headerMismatch) return;

        super.visitTryCatchBlock(start, end, handler, type != null ? transformationContext.toComputedType(type).getInternalName() : null);
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return headerMismatch ? null : super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        if (headerMismatch) return;

        super.visitLocalVariable(name, transformationContext.toComputedType(Type.getType(descriptor)).getDescriptor(), signature, start, end, index);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
        return headerMismatch ? null : super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        if (!headerMismatch) {
            super.visitLineNumber(line, start);
        }
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        if (!headerMismatch) {
            super.visitAttribute(attribute);
        }
    }
}
