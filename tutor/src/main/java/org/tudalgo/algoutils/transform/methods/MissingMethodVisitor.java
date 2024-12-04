package org.tudalgo.algoutils.transform.methods;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.tudalgo.algoutils.transform.classes.ClassInfo;
import org.tudalgo.algoutils.transform.util.*;

import java.util.EnumMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.F_CHOP;

public class MissingMethodVisitor extends BaseMethodVisitor {

    private final Map<LocalsObject, Integer> localsIndexes = new EnumMap<>(LocalsObject.class);

    public MissingMethodVisitor(MethodVisitor delegate,
                                TransformationContext transformationContext,
                                ClassInfo classInfo,
                                MethodHeader methodHeader) {
        super(delegate, transformationContext, classInfo, methodHeader, methodHeader);

        localsIndexes.put(LocalsObject.SUBMISSION_EXECUTION_HANDLER, nextLocalsIndex);
        localsIndexes.put(LocalsObject.METHOD_HEADER, nextLocalsIndex + 1);
        localsIndexes.put(LocalsObject.METHOD_SUBSTITUTION, nextLocalsIndex + 2);
        localsIndexes.put(LocalsObject.CONSTRUCTOR_INVOCATION, nextLocalsIndex + 3);
    }

    @Override
    protected int getLocalsIndex(LocalsObject localsObject) {
        return localsIndexes.get(localsObject);
    }

    @Override
    public void visitCode() {
        Label submissionExecutionHandlerVarLabel = new Label();
        Label methodHeaderVarLabel = new Label();
        Label substitutionCheckLabel = new Label();
        Label delegationCheckLabel = new Label();
        Label delegationCodeLabel = new Label();
        Label submissionCodeLabel = new Label();

        // Setup
        injectSetupCode(submissionExecutionHandlerVarLabel, methodHeaderVarLabel);

        // Invocation logging
        injectInvocationLoggingCode(substitutionCheckLabel);

        // Method substitution
        injectSubstitutionCode(substitutionCheckLabel, delegationCheckLabel);

        // Method delegation
        // check if call should be delegated to solution or not
        delegate.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
        delegate.visitLabel(delegationCheckLabel);
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.SUBMISSION_EXECUTION_HANDLER));
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_HEADER));
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBMISSION_IMPL.toMethodInsn(getDelegate(), false);
        delegate.visitJumpInsn(IFEQ, submissionCodeLabel); // jump to label if useSubmissionImpl(...) == false

        // replay instructions from solution
        delegate.visitFrame(F_CHOP, 2, null, 0, null);
        fullFrameLocals.removeLast();
        fullFrameLocals.removeLast();
        delegate.visitLabel(delegationCodeLabel);
        LocalsObject.SUBMISSION_EXECUTION_HANDLER.visitLocalVariable(this, submissionExecutionHandlerVarLabel, delegationCodeLabel);
        LocalsObject.METHOD_HEADER.visitLocalVariable(this, methodHeaderVarLabel, delegationCodeLabel);
        IncompatibleHeaderException.replicateInBytecode(delegate, true,
            "Method has incorrect return or parameter types", computedMethodHeader, null);

        delegate.visitFrame(F_SAME, 0, null, 0, null);
        delegate.visitLabel(submissionCodeLabel);
        delegate.visitCode();
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        delegate.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        MethodHeader methodHeader = new MethodHeader(owner, name, descriptor);
        if (transformationContext.methodHasReplacement(methodHeader)) {
            transformationContext.getMethodReplacement(methodHeader).toMethodInsn(getDelegate(), false);
        } else {
            delegate.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
