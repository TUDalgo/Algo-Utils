package org.tudalgo.algoutils.transform.methods;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.tudalgo.algoutils.transform.SubmissionClassInfo;
import org.tudalgo.algoutils.transform.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.F_CHOP;
import static org.tudalgo.algoutils.transform.util.TransformationUtils.*;
import static org.tudalgo.algoutils.transform.util.TransformationUtils.unboxType;

public class InjectingMethodVisitor extends BaseMethodVisitor {

    private final int submissionExecutionHandlerIndex;
    private final int methodHeaderIndex;
    private final int methodSubstitutionIndex;
    private final int constructorInvocationIndex;

    public InjectingMethodVisitor(MethodVisitor delegate,
                                  TransformationContext transformationContext,
                                  SubmissionClassInfo submissionClassInfo,
                                  MethodHeader methodHeader) {
        super(delegate, transformationContext, submissionClassInfo, methodHeader, methodHeader);

        this.submissionExecutionHandlerIndex = nextLocalsIndex;
        this.methodHeaderIndex = nextLocalsIndex + 1;
        this.methodSubstitutionIndex = nextLocalsIndex + 2;
        this.constructorInvocationIndex = nextLocalsIndex + 3;
    }

    @Override
    public void visitCode() {
        Label submissionExecutionHandlerVarLabel = new Label();
        Label methodHeaderVarLabel = new Label();
        Label substitutionCheckLabel = new Label();
        Label substitutionStartLabel = new Label();
        Label substitutionEndLabel = new Label();
        Label delegationCheckLabel = new Label();
        Label delegationCodeLabel = new Label();
        Label submissionCodeLabel = new Label();

        // Setup
        {
            // create SubmissionExecutionHandler$Internal instance and store in locals array
            delegate.visitTypeInsn(NEW, Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName());
            delegate.visitInsn(DUP);
            Constants.SUBMISSION_EXECUTION_HANDLER_GET_INSTANCE.toMethodInsn(getDelegate(), false);
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_CONSTRUCTOR.toMethodInsn(getDelegate(), false);
            delegate.visitVarInsn(ASTORE, submissionExecutionHandlerIndex);
            delegate.visitLabel(submissionExecutionHandlerVarLabel);

            // replicate method header in bytecode and store in locals array
            computedMethodHeader.buildHeader(getDelegate());
            delegate.visitVarInsn(ASTORE, methodHeaderIndex);
            delegate.visitLabel(methodHeaderVarLabel);

            delegate.visitFrame(F_APPEND,
                2,
                new Object[] {Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName(), computedMethodHeader.getType().getInternalName()},
                0,
                null);
            fullFrameLocals.add(Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName());
            fullFrameLocals.add(computedMethodHeader.getType().getInternalName());
        }

        // Invocation logging
        {
            // check if invocation should be logged
            delegate.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            delegate.visitVarInsn(ALOAD, methodHeaderIndex);
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_LOG_INVOCATION.toMethodInsn(getDelegate(), false);
            delegate.visitJumpInsn(IFEQ, substitutionCheckLabel); // jump to label if logInvocation(...) == false

            // intercept parameters
            delegate.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            delegate.visitVarInsn(ALOAD, methodHeaderIndex);
            buildInvocation(Type.getArgumentTypes(computedMethodHeader.descriptor()));
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_ADD_INVOCATION.toMethodInsn(getDelegate(), false);
        }

        // Method substitution
        {
            // check if substitution exists for this method
            delegate.visitFrame(F_SAME, 0, null, 0, null);
            delegate.visitLabel(substitutionCheckLabel);
            delegate.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            delegate.visitVarInsn(ALOAD, methodHeaderIndex);
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBSTITUTION.toMethodInsn(getDelegate(), false);
            delegate.visitJumpInsn(IFEQ, delegationCheckLabel); // jump to label if useSubstitution(...) == false

            // get substitution and execute it
            delegate.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            delegate.visitVarInsn(ALOAD, methodHeaderIndex);
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_GET_SUBSTITUTION.toMethodInsn(getDelegate(), false);
            delegate.visitVarInsn(ASTORE, methodSubstitutionIndex);
            delegate.visitFrame(F_APPEND, 1, new Object[] {Constants.METHOD_SUBSTITUTION_TYPE.getInternalName()}, 0, null);
            fullFrameLocals.add(Constants.METHOD_SUBSTITUTION_TYPE.getInternalName());
            delegate.visitLabel(substitutionStartLabel);

            if (isConstructor) {
                List<MethodHeader> superConstructors = submissionClassInfo.getOriginalSuperClassConstructorHeaders()
                    .stream()
                    .map(mh -> submissionClassInfo.getComputedSuperClassConstructorHeader(mh.descriptor()))
                    .toList();
                List<MethodHeader> constructors = submissionClassInfo.getOriginalMethodHeaders()
                    .stream()
                    .filter(mh -> mh.name().equals("<init>"))
                    .map(mh -> submissionClassInfo.getComputedMethodHeader(mh.name(), mh.descriptor()))
                    .filter(mh -> !mh.descriptor().equals(computedMethodHeader.descriptor()))
                    .toList();
                Label[] labels = Stream.generate(Label::new)
                    .limit(superConstructors.size() + constructors.size() + 1)
                    .toArray(Label[]::new);
                Label substitutionExecuteLabel = new Label();
                AtomicInteger labelIndex = new AtomicInteger();

                /*
                 * Representation in source code:
                 * MethodSubstitution.ConstructorInvocation cb = methodSubstitution.getConstructorInvocation();
                 * if (cb.owner().equals(<superclass>) && cb.descriptor().equals(<descriptor>)) {
                 *     super(...);
                 * } else if ...  // for every superclass constructor
                 * else if (cb.owner().equals(<this class>) && cb.descriptor().equals(<descriptor>)) {
                 *     this(...);
                 * } else if ...  // for every regular constructor
                 * else {
                 *     throw new IllegalArgumentException(...);
                 * }
                 */
                delegate.visitVarInsn(ALOAD, methodSubstitutionIndex);
                Constants.METHOD_SUBSTITUTION_GET_CONSTRUCTOR_INVOCATION.toMethodInsn(getDelegate(), true);
                delegate.visitVarInsn(ASTORE, constructorInvocationIndex);
                delegate.visitFrame(F_APPEND, 1, new Object[] {Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName()}, 0, null);
                fullFrameLocals.add(Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName());
                delegate.visitLabel(labels[0]);
                for (MethodHeader superConstructorHeader : superConstructors) {
                    buildConstructorInvocationBranch(superConstructorHeader, substitutionExecuteLabel, labels, labelIndex);
                }
                for (MethodHeader constructorHeader : constructors) {
                    buildConstructorInvocationBranch(constructorHeader, substitutionExecuteLabel, labels, labelIndex);
                }

                // if no matching constructor was found, throw an IllegalArgumentException
                {
                    Type illegalArgumentExceptionType = Type.getType(IllegalArgumentException.class);
                    delegate.visitTypeInsn(NEW, illegalArgumentExceptionType.getInternalName());
                    delegate.visitInsn(DUP);

                    delegate.visitLdcInsn("No matching constructor was found for owner %s and descriptor %s");
                    delegate.visitInsn(ICONST_2);
                    delegate.visitTypeInsn(ANEWARRAY, Constants.STRING_TYPE.getInternalName());
                    delegate.visitInsn(DUP);
                    delegate.visitInsn(ICONST_0);
                    delegate.visitVarInsn(ALOAD, constructorInvocationIndex);
                    Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_OWNER.toMethodInsn(getDelegate(), false);
                    delegate.visitInsn(AASTORE);
                    delegate.visitInsn(DUP);
                    delegate.visitInsn(ICONST_1);
                    delegate.visitVarInsn(ALOAD, constructorInvocationIndex);
                    Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_DESCRIPTOR.toMethodInsn(getDelegate(), false);
                    delegate.visitInsn(AASTORE);
                    delegate.visitMethodInsn(INVOKEVIRTUAL,
                        Constants.STRING_TYPE.getInternalName(),
                        "formatted",
                        Type.getMethodDescriptor(Constants.STRING_TYPE, Constants.OBJECT_ARRAY_TYPE),
                        false);

                    delegate.visitMethodInsn(INVOKESPECIAL,
                        illegalArgumentExceptionType.getInternalName(),
                        "<init>",
                        Type.getMethodDescriptor(Type.VOID_TYPE, Constants.STRING_TYPE),
                        false);
                    delegate.visitInsn(ATHROW);
                }

                fullFrameLocals.removeLast();
                List<Object> locals = new ArrayList<>(fullFrameLocals);
                locals.set(0, computedMethodHeader.owner());
                delegate.visitFrame(F_FULL, locals.size(), locals.toArray(), 0, null);
                delegate.visitLabel(substitutionExecuteLabel);
                delegate.visitLocalVariable("constructorInvocation",
                    Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getDescriptor(),
                    null,
                    labels[labelIndex.get()],
                    substitutionExecuteLabel,
                    constructorInvocationIndex);
            }

            delegate.visitVarInsn(ALOAD, methodSubstitutionIndex);
            buildInvocation(Type.getArgumentTypes(computedMethodHeader.descriptor()));
            Constants.METHOD_SUBSTITUTION_EXECUTE.toMethodInsn(getDelegate(), true);
            Type returnType = Type.getReturnType(computedMethodHeader.descriptor());
            if (returnType.getSort() == Type.ARRAY || returnType.getSort() == Type.OBJECT) {
                delegate.visitTypeInsn(CHECKCAST, returnType.getInternalName());
            } else {
                unboxType(getDelegate(), returnType);
            }
            delegate.visitInsn(returnType.getOpcode(IRETURN));
            delegate.visitLabel(substitutionEndLabel);
            delegate.visitLocalVariable("methodSubstitution",
                Constants.METHOD_SUBSTITUTION_TYPE.getDescriptor(),
                null,
                substitutionStartLabel,
                substitutionEndLabel,
                methodSubstitutionIndex);
        }

        // Method delegation
        // if only default transformations are applied, skip delegation
        {
            // check if call should be delegated to solution or not
            fullFrameLocals.removeLast();
            delegate.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
            delegate.visitLabel(delegationCheckLabel);
            delegate.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            delegate.visitVarInsn(ALOAD, methodHeaderIndex);
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBMISSION_IMPL.toMethodInsn(getDelegate(), false);
            delegate.visitJumpInsn(IFEQ, submissionCodeLabel); // jump to label if useSubmissionImpl(...) == false

            // replay instructions from solution
            delegate.visitFrame(F_CHOP, 2, null, 0, null);
            fullFrameLocals.removeLast();
            fullFrameLocals.removeLast();
            delegate.visitLabel(delegationCodeLabel);
            delegate.visitLocalVariable("submissionExecutionHandler",
                Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getDescriptor(),
                null,
                submissionExecutionHandlerVarLabel,
                delegationCodeLabel,
                submissionExecutionHandlerIndex);
            delegate.visitLocalVariable("methodHeader",
                computedMethodHeader.getType().getDescriptor(),
                null,
                methodHeaderVarLabel,
                delegationCodeLabel,
                methodHeaderIndex);
            new IncompatibleHeaderException("Method has incorrect return or parameter types", computedMethodHeader, null)
                .replicateInBytecode(getDelegate(), true);

            delegate.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
            delegate.visitLabel(submissionCodeLabel);
        }

        delegate.visitCode();
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (headerMismatch) return;

        // skip transformation if owner is not part of the submission
        if (!transformationContext.isSubmissionClass(owner)) {
            delegate.visitFieldInsn(opcode, owner, name, descriptor);
        } else {
            if (owner.startsWith("[")) {  // stupid edge cases
                delegate.visitFieldInsn(opcode,
                    transformationContext.toComputedType(owner).getInternalName(),
                    name,
                    transformationContext.toComputedType(descriptor).getDescriptor());
            } else {
                FieldHeader computedFieldHeader = transformationContext.getSubmissionClassInfo(owner).getComputedFieldHeader(name);
                if (TransformationUtils.opcodeIsCompatible(opcode, computedFieldHeader.access())) {
                    delegate.visitFieldInsn(opcode,
                        transformationContext.toComputedType(computedFieldHeader.owner()).getInternalName(),
                        computedFieldHeader.name(),
                        computedFieldHeader.descriptor());
                } else { // if incompatible
                    delegate.visitFieldInsn(opcode, computedFieldHeader.owner(), name + "$submission", computedFieldHeader.descriptor());
                }
            }
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (headerMismatch) return;

        // skip transformation if owner is not part of the submission
        MethodHeader methodHeader = new MethodHeader(owner, 0, name, descriptor, null, null);
        if (transformationContext.methodHasReplacement(methodHeader)) {
            transformationContext.getMethodReplacement(methodHeader).toMethodInsn(getDelegate(), false);
        } else {
            delegate.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    /**
     * Builds an {@link Invocation} in bytecode.
     *
     * @param argumentTypes an array of parameter types
     */
    private void buildInvocation(Type[] argumentTypes) {
        Type threadType = Type.getType(Thread.class);
        Type stackTraceElementArrayType = Type.getType(StackTraceElement[].class);

        delegate.visitTypeInsn(NEW, Constants.INVOCATION_TYPE.getInternalName());
        delegate.visitInsn(DUP);
        delegate.visitLdcInsn(Type.getObjectType(computedMethodHeader.owner()));
        computedMethodHeader.buildHeader(getDelegate());
        delegate.visitMethodInsn(INVOKESTATIC,
            threadType.getInternalName(),
            "currentThread",
            Type.getMethodDescriptor(threadType),
            false);
        delegate.visitMethodInsn(INVOKEVIRTUAL,
            threadType.getInternalName(),
            "getStackTrace",
            Type.getMethodDescriptor(stackTraceElementArrayType),
            false);
        if (!isStatic && !isConstructor) {
            delegate.visitVarInsn(ALOAD, 0);
            Constants.INVOCATION_CONSTRUCTOR_WITH_INSTANCE.toMethodInsn(getDelegate(), false);
        } else {
            Constants.INVOCATION_CONSTRUCTOR.toMethodInsn(getDelegate(), false);
        }
        // load parameter with opcode (ALOAD, ILOAD, etc.) for type and ignore "this", if it exists
        for (int i = 0; i < argumentTypes.length; i++) {
            delegate.visitInsn(DUP);
            delegate.visitVarInsn(argumentTypes[i].getOpcode(ILOAD), getLocalsIndex(argumentTypes, i) + (isStatic ? 0 : 1));
            boxType(getDelegate(), argumentTypes[i]);
            Constants.INVOCATION_CONSTRUCTOR_ADD_PARAMETER.toMethodInsn(getDelegate(), false);
        }
    }

    private void buildConstructorInvocationBranch(MethodHeader constructorHeader,
                                                  Label substitutionExecuteLabel,
                                                  Label[] labels,
                                                  AtomicInteger labelIndex) {
        delegate.visitVarInsn(ALOAD, constructorInvocationIndex);
        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_OWNER.toMethodInsn(getDelegate(), false);
        delegate.visitLdcInsn(constructorHeader.owner());
        delegate.visitMethodInsn(INVOKEVIRTUAL,
            Constants.STRING_TYPE.getInternalName(),
            "equals",
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Constants.OBJECT_TYPE),
            false);

        delegate.visitVarInsn(ALOAD, constructorInvocationIndex);
        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_DESCRIPTOR.toMethodInsn(getDelegate(), false);
        delegate.visitLdcInsn(constructorHeader.descriptor());
        delegate.visitMethodInsn(INVOKEVIRTUAL,
            Constants.STRING_TYPE.getInternalName(),
            "equals",
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Constants.OBJECT_TYPE),
            false);

        delegate.visitInsn(IAND);
        delegate.visitJumpInsn(IFEQ, labels[labelIndex.get() + 1]);  // jump to next branch if false

        Label argsVarStartLabel = new Label();
        delegate.visitVarInsn(ALOAD, constructorInvocationIndex);
        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_ARGS.toMethodInsn(getDelegate(), false);
        delegate.visitVarInsn(ASTORE, constructorInvocationIndex + 1);
        delegate.visitFrame(F_APPEND, 1, new Object[] {Constants.OBJECT_ARRAY_TYPE.getInternalName()}, 0, null);
        fullFrameLocals.add(Constants.OBJECT_ARRAY_TYPE.getInternalName());
        delegate.visitLabel(argsVarStartLabel);

        delegate.visitVarInsn(ALOAD, 0);
        Type[] parameterTypes = Type.getArgumentTypes(constructorHeader.descriptor());
        for (int i = 0; i < parameterTypes.length; i++) {  // unpack array
            delegate.visitVarInsn(ALOAD, constructorInvocationIndex + 1);
            delegate.visitIntInsn(SIPUSH, i);
            delegate.visitInsn(AALOAD);
            unboxType(getDelegate(), parameterTypes[i]);
        }
        constructorHeader.toMethodInsn(getDelegate(), false);
        delegate.visitJumpInsn(GOTO, substitutionExecuteLabel);

        fullFrameLocals.removeLast();
        delegate.visitFrame(F_CHOP, 1, null, 0, new Object[0]);
        delegate.visitLabel(labels[labelIndex.incrementAndGet()]);
        delegate.visitLocalVariable("args",
            Constants.OBJECT_ARRAY_TYPE.getDescriptor(),
            null,
            argsVarStartLabel,
            labels[labelIndex.get()],
            constructorInvocationIndex + 1);
    }
}
