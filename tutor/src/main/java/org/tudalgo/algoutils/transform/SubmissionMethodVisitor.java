package org.tudalgo.algoutils.transform;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.tudalgo.algoutils.transform.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.tudalgo.algoutils.transform.util.TransformationUtils.*;
import static org.tudalgo.algoutils.transform.util.TransformationUtils.boxType;

/**
 * A method visitor for transforming submission methods.
 *
 * @see SubmissionClassVisitor
 * @author Daniel Mangold
 */
class SubmissionMethodVisitor extends MethodVisitor {

    private final TransformationContext transformationContext;
    private final MethodHeader methodHeader;
    private final SubmissionClassInfo submissionClassInfo;
    private final String className;
    private final boolean defaultTransformationsOnly;

    private final boolean isStatic;
    private final boolean isConstructor;

    private final int submissionExecutionHandlerIndex;
    private final int methodHeaderIndex;
    private final int methodSubstitutionIndex;
    private final int constructorInvocationIndex;

    private final List<Object> fullFrameLocals;

    /**
     * Constructs a new {@link SubmissionMethodVisitor}.
     *
     * @param delegate              the method visitor to delegate to
     * @param transformationContext the transformation context
     * @param submissionClassInfo   information about the submission class this method belongs to
     * @param methodHeader          the computed method header of this method
     */
    SubmissionMethodVisitor(MethodVisitor delegate,
                            TransformationContext transformationContext,
                            SubmissionClassInfo submissionClassInfo,
                            MethodHeader methodHeader) {
        super(ASM9, delegate);
        this.transformationContext = transformationContext;
        this.submissionClassInfo = submissionClassInfo;
        this.methodHeader = methodHeader;
        this.className = submissionClassInfo.getComputedClassName();
        this.defaultTransformationsOnly = submissionClassInfo.getSolutionClass().isEmpty();

        this.isStatic = (methodHeader.access() & ACC_STATIC) != 0;
        this.isConstructor = methodHeader.name().equals("<init>");

        // calculate length of locals array, including "this" if applicable
        int nextLocalsIndex = (Type.getArgumentsAndReturnSizes(methodHeader.descriptor()) >> 2) - (isStatic ? 1 : 0);
        this.submissionExecutionHandlerIndex = nextLocalsIndex;
        this.methodHeaderIndex = nextLocalsIndex + 1;
        this.methodSubstitutionIndex = nextLocalsIndex + 2;
        this.constructorInvocationIndex = nextLocalsIndex + 3;

        this.fullFrameLocals = Arrays.stream(Type.getArgumentTypes(methodHeader.descriptor()))
            .map(type -> switch (type.getSort()) {
                case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> INTEGER;
                case Type.FLOAT -> FLOAT;
                case Type.LONG -> LONG;
                case Type.DOUBLE -> DOUBLE;
                default -> type.getInternalName();
            })
            .collect(Collectors.toList());
        if (!isStatic) {
            this.fullFrameLocals.addFirst(isConstructor ? UNINITIALIZED_THIS : className);
        }
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
            super.visitTypeInsn(NEW, Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName());
            super.visitInsn(DUP);
            super.visitMethodInsn(INVOKESTATIC,
                Constants.SUBMISSION_EXECUTION_HANDLER_TYPE.getInternalName(),
                "getInstance",
                Type.getMethodDescriptor(Constants.SUBMISSION_EXECUTION_HANDLER_TYPE),
                false);
            super.visitMethodInsn(INVOKESPECIAL,
                Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName(),
                "<init>",
                Type.getMethodDescriptor(Type.VOID_TYPE, Constants.SUBMISSION_EXECUTION_HANDLER_TYPE),
                false);
            super.visitVarInsn(ASTORE, submissionExecutionHandlerIndex);
            super.visitLabel(submissionExecutionHandlerVarLabel);

            // replicate method header in bytecode and store in locals array
            buildMethodHeader(getDelegate(), methodHeader);
            super.visitVarInsn(ASTORE, methodHeaderIndex);
            super.visitLabel(methodHeaderVarLabel);

            super.visitFrame(F_APPEND,
                2,
                new Object[] {Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName(), methodHeader.getType().getInternalName()},
                0,
                null);
            fullFrameLocals.add(Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName());
            fullFrameLocals.add(methodHeader.getType().getInternalName());
        }

        // Invocation logging
        {
            // check if invocation should be logged
            super.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            super.visitVarInsn(ALOAD, methodHeaderIndex);
            super.visitMethodInsn(INVOKEVIRTUAL,
                Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName(),
                "logInvocation",
                Type.getMethodDescriptor(Type.BOOLEAN_TYPE, methodHeader.getType()),
                false);
            super.visitJumpInsn(IFEQ, substitutionCheckLabel); // jump to label if logInvocation(...) == false

            // intercept parameters
            super.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            super.visitVarInsn(ALOAD, methodHeaderIndex);
            buildInvocation(Type.getArgumentTypes(methodHeader.descriptor()));
            super.visitMethodInsn(INVOKEVIRTUAL,
                Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName(),
                "addInvocation",
                Type.getMethodDescriptor(Type.VOID_TYPE, methodHeader.getType(), Constants.INVOCATION_TYPE),
                false);
        }

        // Method substitution
        {
            // check if substitution exists for this method
            super.visitFrame(F_SAME, 0, null, 0, null);
            super.visitLabel(substitutionCheckLabel);
            super.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            super.visitVarInsn(ALOAD, methodHeaderIndex);
            super.visitMethodInsn(INVOKEVIRTUAL,
                Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName(),
                "useSubstitution",
                Type.getMethodDescriptor(Type.BOOLEAN_TYPE, methodHeader.getType()),
                false);
            super.visitJumpInsn(IFEQ, defaultTransformationsOnly ? submissionCodeLabel : delegationCheckLabel); // jump to label if useSubstitution(...) == false

            // get substitution and execute it
            super.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            super.visitVarInsn(ALOAD, methodHeaderIndex);
            super.visitMethodInsn(INVOKEVIRTUAL,
                Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName(),
                "getSubstitution",
                Type.getMethodDescriptor(Constants.METHOD_SUBSTITUTION_TYPE, methodHeader.getType()),
                false);
            super.visitVarInsn(ASTORE, methodSubstitutionIndex);
            super.visitFrame(F_APPEND, 1, new Object[] {Constants.METHOD_SUBSTITUTION_TYPE.getInternalName()}, 0, null);
            fullFrameLocals.add(Constants.METHOD_SUBSTITUTION_TYPE.getInternalName());
            super.visitLabel(substitutionStartLabel);

            if (isConstructor) {
                List<MethodHeader> superConstructors = submissionClassInfo.getOriginalSuperClassConstructorHeaders()
                    .stream()
                    .map(mh -> submissionClassInfo.getComputedSuperClassConstructorHeader(mh.descriptor()))
                    .toList();
                List<MethodHeader> constructors = submissionClassInfo.getOriginalMethodHeaders()
                    .stream()
                    .filter(mh -> mh.name().equals("<init>"))
                    .map(mh -> submissionClassInfo.getComputedMethodHeader(mh.name(), mh.descriptor()))
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
                super.visitVarInsn(ALOAD, methodSubstitutionIndex);
                super.visitMethodInsn(INVOKEINTERFACE,
                    Constants.METHOD_SUBSTITUTION_TYPE.getInternalName(),
                    "getConstructorInvocation",
                    Type.getMethodDescriptor(Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE),
                    true);
                super.visitVarInsn(ASTORE, constructorInvocationIndex);
                super.visitFrame(F_APPEND, 1, new Object[] {Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName()}, 0, null);
                fullFrameLocals.add(Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName());
                super.visitLabel(labels[0]);
                for (MethodHeader superConstructorHeader : superConstructors) {
                    buildConstructorInvocationBranch(superConstructorHeader, substitutionExecuteLabel, labels, labelIndex);
                }
                for (MethodHeader constructorHeader : constructors) {
                    buildConstructorInvocationBranch(constructorHeader, substitutionExecuteLabel, labels, labelIndex);
                }

                // if no matching constructor was found, throw an IllegalArgumentException
                {
                    Type illegalArgumentExceptionType = Type.getType(IllegalArgumentException.class);
                    super.visitTypeInsn(NEW, illegalArgumentExceptionType.getInternalName());
                    super.visitInsn(DUP);

                    super.visitLdcInsn("No matching constructor was found for owner %s and descriptor %s");
                    super.visitInsn(ICONST_2);
                    super.visitTypeInsn(ANEWARRAY, Constants.STRING_TYPE.getInternalName());
                    super.visitInsn(DUP);
                    super.visitInsn(ICONST_0);
                    super.visitVarInsn(ALOAD, constructorInvocationIndex);
                    super.visitMethodInsn(INVOKEVIRTUAL,
                        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName(),
                        "owner",
                        Type.getMethodDescriptor(Constants.STRING_TYPE),
                        false);
                    super.visitInsn(AASTORE);
                    super.visitInsn(DUP);
                    super.visitInsn(ICONST_1);
                    super.visitVarInsn(ALOAD, constructorInvocationIndex);
                    super.visitMethodInsn(INVOKEVIRTUAL,
                        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName(),
                        "descriptor",
                        Type.getMethodDescriptor(Constants.STRING_TYPE),
                        false);
                    super.visitInsn(AASTORE);
                    super.visitMethodInsn(INVOKEVIRTUAL,
                        Constants.STRING_TYPE.getInternalName(),
                        "formatted",
                        Type.getMethodDescriptor(Constants.STRING_TYPE, Constants.OBJECT_ARRAY_TYPE),
                        false);

                    super.visitMethodInsn(INVOKESPECIAL,
                        illegalArgumentExceptionType.getInternalName(),
                        "<init>",
                        Type.getMethodDescriptor(Type.VOID_TYPE, Constants.STRING_TYPE),
                        false);
                    super.visitInsn(ATHROW);
                }

                fullFrameLocals.removeLast();
                List<Object> locals = new ArrayList<>(fullFrameLocals);
                locals.set(0, className);
                super.visitFrame(F_FULL, locals.size(), locals.toArray(), 0, null);
                super.visitLabel(substitutionExecuteLabel);
                super.visitLocalVariable("constructorInvocation",
                    Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getDescriptor(),
                    null,
                    labels[labelIndex.get()],
                    substitutionExecuteLabel,
                    constructorInvocationIndex);
            }

            super.visitVarInsn(ALOAD, methodSubstitutionIndex);
            buildInvocation(Type.getArgumentTypes(methodHeader.descriptor()));
            super.visitMethodInsn(INVOKEINTERFACE,
                Constants.METHOD_SUBSTITUTION_TYPE.getInternalName(),
                "execute",
                Type.getMethodDescriptor(Constants.OBJECT_TYPE, Constants.INVOCATION_TYPE),
                true);
            Type returnType = Type.getReturnType(methodHeader.descriptor());
            if (returnType.getSort() == Type.ARRAY || returnType.getSort() == Type.OBJECT) {
                super.visitTypeInsn(CHECKCAST, returnType.getInternalName());
            } else {
                unboxType(getDelegate(), returnType);
            }
            super.visitInsn(returnType.getOpcode(IRETURN));
            super.visitLabel(substitutionEndLabel);
            super.visitLocalVariable("methodSubstitution",
                Constants.METHOD_SUBSTITUTION_TYPE.getDescriptor(),
                null,
                substitutionStartLabel,
                substitutionEndLabel,
                methodSubstitutionIndex);
        }

        // Method delegation
        // if only default transformations are applied, skip delegation
        if (!defaultTransformationsOnly) {
            // check if call should be delegated to solution or not
            fullFrameLocals.removeLast();
            super.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
            super.visitLabel(delegationCheckLabel);
            super.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            super.visitVarInsn(ALOAD, methodHeaderIndex);
            super.visitMethodInsn(INVOKEVIRTUAL,
                Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName(),
                "useSubmissionImpl",
                Type.getMethodDescriptor(Type.BOOLEAN_TYPE, methodHeader.getType()),
                false);
            super.visitJumpInsn(IFNE, submissionCodeLabel); // jump to label if useSubmissionImpl(...) == true

            // replay instructions from solution
            super.visitFrame(F_CHOP, 2, null, 0, null);
            fullFrameLocals.removeLast();
            fullFrameLocals.removeLast();
            super.visitLabel(delegationCodeLabel);
            super.visitLocalVariable("submissionExecutionHandler",
                Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getDescriptor(),
                null,
                submissionExecutionHandlerVarLabel,
                delegationCodeLabel,
                submissionExecutionHandlerIndex);
            super.visitLocalVariable("methodHeader",
                methodHeader.getType().getDescriptor(),
                null,
                methodHeaderVarLabel,
                delegationCodeLabel,
                methodHeaderIndex);
            //noinspection OptionalGetWithoutIsPresent
            submissionClassInfo.getSolutionClass().get().getMethods().get(methodHeader).accept(getDelegate());

            super.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
            super.visitLabel(submissionCodeLabel);
        } else {
            fullFrameLocals.removeLast();
            fullFrameLocals.removeLast();
            fullFrameLocals.removeLast();
            super.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
            super.visitLabel(submissionCodeLabel);
            super.visitLocalVariable("submissionExecutionHandler",
                Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getDescriptor(),
                null,
                submissionExecutionHandlerVarLabel,
                submissionCodeLabel,
                submissionExecutionHandlerIndex);
            super.visitLocalVariable("methodHeader",
                methodHeader.getType().getDescriptor(),
                null,
                methodHeaderVarLabel,
                submissionCodeLabel,
                methodHeaderIndex);
        }

        // visit original code
        super.visitCode();
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        // skip transformation if only default transformations are applied or owner is not part of the submission
        if (defaultTransformationsOnly || !owner.startsWith(transformationContext.getProjectPrefix())) {
            super.visitFieldInsn(opcode, owner, name, descriptor);
        } else {
            FieldHeader fieldHeader = transformationContext.getSubmissionClassInfo(owner).getComputedFieldHeader(name);
            super.visitFieldInsn(opcode, fieldHeader.owner(), fieldHeader.name(), fieldHeader.descriptor());
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        // skip transformation if only default transformations are applied or owner is not part of the submission
        MethodHeader methodHeader = new MethodHeader(owner, 0, name, descriptor, null, null);
        if (transformationContext.methodHasReplacement(methodHeader)) {
            MethodHeader replacementMethodHeader = transformationContext.getMethodReplacement(methodHeader);
            super.visitMethodInsn(INVOKESTATIC,
                replacementMethodHeader.owner(),
                replacementMethodHeader.name(),
                replacementMethodHeader.descriptor(),
                false);
        } else if (defaultTransformationsOnly || !owner.startsWith(transformationContext.getProjectPrefix())) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        } else {
            methodHeader = transformationContext.getSubmissionClassInfo(owner).getComputedMethodHeader(name, descriptor);
            super.visitMethodInsn(opcode, methodHeader.owner(), methodHeader.name(), methodHeader.descriptor(), isInterface);
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

        super.visitTypeInsn(NEW, Constants.INVOCATION_TYPE.getInternalName());
        super.visitInsn(DUP);
        if (!isStatic && !isConstructor) {
            super.visitVarInsn(ALOAD, 0);
            super.visitMethodInsn(INVOKESTATIC,
                threadType.getInternalName(),
                "currentThread",
                Type.getMethodDescriptor(threadType),
                false);
            super.visitMethodInsn(INVOKEVIRTUAL,
                threadType.getInternalName(),
                "getStackTrace",
                Type.getMethodDescriptor(stackTraceElementArrayType),
                false);
            super.visitMethodInsn(INVOKESPECIAL,
                Constants.INVOCATION_TYPE.getInternalName(),
                "<init>",
                Type.getMethodDescriptor(Type.VOID_TYPE, Constants.OBJECT_TYPE, stackTraceElementArrayType),
                false);
        } else {
            super.visitMethodInsn(INVOKESTATIC,
                threadType.getInternalName(),
                "currentThread",
                Type.getMethodDescriptor(threadType),
                false);
            super.visitMethodInsn(INVOKEVIRTUAL,
                threadType.getInternalName(),
                "getStackTrace",
                Type.getMethodDescriptor(stackTraceElementArrayType),
                false);
            super.visitMethodInsn(INVOKESPECIAL,
                Constants.INVOCATION_TYPE.getInternalName(),
                "<init>",
                Type.getMethodDescriptor(Type.VOID_TYPE, stackTraceElementArrayType),
                false);
        }
        // load parameter with opcode (ALOAD, ILOAD, etc.) for type and ignore "this", if it exists
        for (int i = 0; i < argumentTypes.length; i++) {
            super.visitInsn(DUP);
            super.visitVarInsn(argumentTypes[i].getOpcode(ILOAD), getLocalsIndex(argumentTypes, i) + (isStatic ? 0 : 1));
            boxType(getDelegate(), argumentTypes[i]);
            super.visitMethodInsn(INVOKEVIRTUAL,
                Constants.INVOCATION_TYPE.getInternalName(),
                "addParameter",
                Type.getMethodDescriptor(Type.VOID_TYPE, Constants.OBJECT_TYPE),
                false);
        }
    }

    private void buildConstructorInvocationBranch(MethodHeader constructorHeader,
                                                  Label substitutionExecuteLabel,
                                                  Label[] labels,
                                                  AtomicInteger labelIndex) {
        super.visitVarInsn(ALOAD, constructorInvocationIndex);
        super.visitMethodInsn(INVOKEVIRTUAL,
            Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName(),
            "owner",
            Type.getMethodDescriptor(Constants.STRING_TYPE),
            false);
        super.visitLdcInsn(constructorHeader.owner());
        super.visitMethodInsn(INVOKEVIRTUAL,
            Constants.STRING_TYPE.getInternalName(),
            "equals",
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Constants.OBJECT_TYPE),
            false);

        super.visitVarInsn(ALOAD, constructorInvocationIndex);
        super.visitMethodInsn(INVOKEVIRTUAL,
            Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName(),
            "descriptor",
            Type.getMethodDescriptor(Constants.STRING_TYPE),
            false);
        super.visitLdcInsn(constructorHeader.descriptor());
        super.visitMethodInsn(INVOKEVIRTUAL,
            Constants.STRING_TYPE.getInternalName(),
            "equals",
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Constants.OBJECT_TYPE),
            false);

        super.visitInsn(IAND);
        super.visitJumpInsn(IFEQ, labels[labelIndex.get() + 1]);  // jump to next branch if false

        Label argsVarStartLabel = new Label();
        super.visitVarInsn(ALOAD, constructorInvocationIndex);
        super.visitMethodInsn(INVOKEVIRTUAL,
            Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName(),
            "args",
            Type.getMethodDescriptor(Constants.OBJECT_ARRAY_TYPE),
            false);
        super.visitVarInsn(ASTORE, constructorInvocationIndex + 1);
        super.visitFrame(F_APPEND, 1, new Object[] {Constants.OBJECT_ARRAY_TYPE.getInternalName()}, 0, null);
        fullFrameLocals.add(Constants.OBJECT_ARRAY_TYPE.getInternalName());
        super.visitLabel(argsVarStartLabel);

        super.visitVarInsn(ALOAD, 0);
        Type[] parameterTypes = Type.getArgumentTypes(constructorHeader.descriptor());
        for (int i = 0; i < parameterTypes.length; i++) {  // unpack array
            super.visitVarInsn(ALOAD, constructorInvocationIndex + 1);
            super.visitIntInsn(SIPUSH, i);
            super.visitInsn(AALOAD);
            unboxType(getDelegate(), parameterTypes[i]);
        }
        super.visitMethodInsn(INVOKESPECIAL,
            constructorHeader.owner(),
            "<init>",
            constructorHeader.descriptor(),
            false);
        super.visitJumpInsn(GOTO, substitutionExecuteLabel);

        fullFrameLocals.removeLast();
        super.visitFrame(F_CHOP, 1, null, 0, new Object[0]);
        super.visitLabel(labels[labelIndex.incrementAndGet()]);
        super.visitLocalVariable("args",
            Constants.OBJECT_ARRAY_TYPE.getDescriptor(),
            null,
            argsVarStartLabel,
            labels[labelIndex.get()],
            constructorInvocationIndex + 1);
    }
}
