package org.tudalgo.algoutils.transform;

import org.objectweb.asm.*;
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
    private final MethodHeader originalMethodHeader;
    private final MethodHeader computedMethodHeader;
    private final SubmissionClassInfo submissionClassInfo;
    private final String className;
    private final boolean defaultTransformationsOnly;

    private final boolean headerMismatch;
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
     * @param originalMethodHeader  the computed method header of this method
     */
    SubmissionMethodVisitor(MethodVisitor delegate,
                            TransformationContext transformationContext,
                            SubmissionClassInfo submissionClassInfo,
                            MethodHeader originalMethodHeader,
                            MethodHeader computedMethodHeader) {
        super(ASM9, delegate);
        this.transformationContext = transformationContext;
        this.submissionClassInfo = submissionClassInfo;
        this.originalMethodHeader = originalMethodHeader;
        this.computedMethodHeader = computedMethodHeader;
        this.className = submissionClassInfo.getComputedClassName();
        this.defaultTransformationsOnly = submissionClassInfo.getSolutionClass()
            .map(solutionClassNode -> !solutionClassNode.getMethods().containsKey(computedMethodHeader))
            .orElse(true);

        this.isStatic = (computedMethodHeader.access() & ACC_STATIC) != 0;
        this.isConstructor = computedMethodHeader.name().equals("<init>");

        // calculate length of locals array, including "this" if applicable
        int nextLocalsIndex = (Type.getArgumentsAndReturnSizes(computedMethodHeader.descriptor()) >> 2) - (isStatic ? 1 : 0);
        this.submissionExecutionHandlerIndex = nextLocalsIndex;
        this.methodHeaderIndex = nextLocalsIndex + 1;
        this.methodSubstitutionIndex = nextLocalsIndex + 2;
        this.constructorInvocationIndex = nextLocalsIndex + 3;

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
            this.fullFrameLocals.addFirst(isConstructor ? UNINITIALIZED_THIS : className);
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
            Constants.SUBMISSION_EXECUTION_HANDLER_GET_INSTANCE.toMethodInsn(getDelegate(), false);
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_CONSTRUCTOR.toMethodInsn(getDelegate(), false);
            super.visitVarInsn(ASTORE, submissionExecutionHandlerIndex);
            super.visitLabel(submissionExecutionHandlerVarLabel);

            // replicate method header in bytecode and store in locals array
            buildHeader(getDelegate(), computedMethodHeader);
            super.visitVarInsn(ASTORE, methodHeaderIndex);
            super.visitLabel(methodHeaderVarLabel);

            super.visitFrame(F_APPEND,
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
            super.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            super.visitVarInsn(ALOAD, methodHeaderIndex);
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_LOG_INVOCATION.toMethodInsn(getDelegate(), false);
            super.visitJumpInsn(IFEQ, substitutionCheckLabel); // jump to label if logInvocation(...) == false

            // intercept parameters
            super.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            super.visitVarInsn(ALOAD, methodHeaderIndex);
            buildInvocation(Type.getArgumentTypes(computedMethodHeader.descriptor()));
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_ADD_INVOCATION.toMethodInsn(getDelegate(), false);
        }

        // Method substitution
        {
            // check if substitution exists for this method
            super.visitFrame(F_SAME, 0, null, 0, null);
            super.visitLabel(substitutionCheckLabel);
            super.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            super.visitVarInsn(ALOAD, methodHeaderIndex);
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBSTITUTION.toMethodInsn(getDelegate(), false);
            super.visitJumpInsn(IFEQ, defaultTransformationsOnly ? submissionCodeLabel : delegationCheckLabel); // jump to label if useSubstitution(...) == false

            // get substitution and execute it
            super.visitVarInsn(ALOAD, submissionExecutionHandlerIndex);
            super.visitVarInsn(ALOAD, methodHeaderIndex);
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_GET_SUBSTITUTION.toMethodInsn(getDelegate(), false);
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
                Constants.METHOD_SUBSTITUTION_GET_CONSTRUCTOR_INVOCATION.toMethodInsn(getDelegate(), true);
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
                    Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_OWNER.toMethodInsn(getDelegate(), false);
                    super.visitInsn(AASTORE);
                    super.visitInsn(DUP);
                    super.visitInsn(ICONST_1);
                    super.visitVarInsn(ALOAD, constructorInvocationIndex);
                    Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_DESCRIPTOR.toMethodInsn(getDelegate(), false);
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
            buildInvocation(Type.getArgumentTypes(computedMethodHeader.descriptor()));
            Constants.METHOD_SUBSTITUTION_EXECUTE.toMethodInsn(getDelegate(), true);
            Type returnType = Type.getReturnType(computedMethodHeader.descriptor());
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
            Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBMISSION_IMPL.toMethodInsn(getDelegate(), false);
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
                computedMethodHeader.getType().getDescriptor(),
                null,
                methodHeaderVarLabel,
                delegationCodeLabel,
                methodHeaderIndex);
            //noinspection OptionalGetWithoutIsPresent
            submissionClassInfo.getSolutionClass().get().getMethods().get(computedMethodHeader).accept(getDelegate());

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
                computedMethodHeader.getType().getDescriptor(),
                null,
                methodHeaderVarLabel,
                submissionCodeLabel,
                methodHeaderIndex);
        }

        if (headerMismatch) {
            TransformationUtils.buildExceptionForHeaderMismatch(getDelegate(),
                "Method has incorrect return or parameter types",
                computedMethodHeader,
                originalMethodHeader);
        } else {
            // visit original code
            super.visitCode();
        }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (headerMismatch) return;

        // skip transformation if owner is not part of the submission
        if (!transformationContext.isSubmissionClass(owner)) {
            super.visitFieldInsn(opcode, owner, name, descriptor);
        } else {
            if (owner.startsWith("[")) {  // stupid edge cases
                Type ownerType = Type.getType(owner);
                Type actualOwner = Type.getObjectType(transformationContext.getSubmissionClassInfo(ownerType.getElementType().getInternalName())
                    .getComputedClassName());
                super.visitFieldInsn(opcode,
                    "[".repeat(ownerType.getDimensions()) + actualOwner.getDescriptor(),
                    name,
                    TransformationUtils.getComputedType(transformationContext, Type.getType(descriptor)).getDescriptor());
            } else {
                FieldHeader computedFieldHeader = transformationContext.getSubmissionClassInfo(owner).getComputedFieldHeader(name);
                boolean isStaticOpcode = opcode == GETSTATIC || opcode == PUTSTATIC;
                boolean isStaticField = (computedFieldHeader.access() & ACC_STATIC) != 0;
                if (isStaticOpcode == isStaticField) {
                    super.visitFieldInsn(opcode,
                        TransformationUtils.getComputedName(transformationContext, computedFieldHeader.owner()),
                        computedFieldHeader.name(),
                        computedFieldHeader.descriptor());
                } else { // if incompatible
                    super.visitFieldInsn(opcode, computedFieldHeader.owner(), name + "$submission", computedFieldHeader.descriptor());
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
        } else if (!transformationContext.isSubmissionClass(owner)) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        } else if (owner.startsWith("[")) {  // stupid edge cases
            Type ownerType = Type.getType(owner);
            Type actualOwner = Type.getObjectType(transformationContext.getSubmissionClassInfo(ownerType.getElementType().getInternalName())
                .getComputedClassName());
            super.visitMethodInsn(opcode, "[".repeat(ownerType.getDimensions()) + actualOwner.getDescriptor(), name, descriptor, isInterface);
        } else {
            methodHeader = transformationContext.getSubmissionClassInfo(owner).getComputedMethodHeader(name, descriptor);
            if ((opcode == INVOKESTATIC) == ((methodHeader.access() & ACC_STATIC) != 0)) {
                super.visitMethodInsn(opcode, methodHeader.owner(), methodHeader.name(), methodHeader.descriptor(), isInterface);
            } else {
                Type returnType = TransformationUtils.getComputedType(transformationContext, Type.getReturnType(descriptor));
                Type[] parameterTypes = Arrays.stream(Type.getArgumentTypes(descriptor))
                    .map(type -> TransformationUtils.getComputedType(transformationContext, type))
                    .toArray(Type[]::new);
                super.visitMethodInsn(opcode,
                    methodHeader.owner(),
                    name + "$submission",
                    Type.getMethodDescriptor(returnType, parameterTypes),
                    isInterface);
            }
        }
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (headerMismatch) return;

        if (value instanceof Type type && transformationContext.isSubmissionClass(type.getInternalName())) {
            if (type.getSort() == Type.OBJECT) {
                value = Type.getObjectType(transformationContext.getSubmissionClassInfo(type.getInternalName()).getComputedClassName());
            } else {  // else must be array
                Type elementType = Type.getObjectType(transformationContext.getSubmissionClassInfo(type.getElementType().getInternalName())
                    .getComputedClassName());
                value = Type.getObjectType("[".repeat(type.getDimensions()) + elementType.getDescriptor());
            }
        }
        super.visitLdcInsn(value);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (headerMismatch) return;

        Type paramType = type.startsWith("[") ? Type.getType(type) : Type.getObjectType(type);
        if (transformationContext.isSubmissionClass(paramType.getInternalName())) {
            if (paramType.getSort() == Type.OBJECT) {
                type = transformationContext.getSubmissionClassInfo(paramType.getInternalName()).getComputedClassName();
            } else {  // else must be array
                Type elementType = Type.getObjectType(transformationContext.getSubmissionClassInfo(paramType.getElementType().getInternalName())
                    .getComputedClassName());
                type = "[".repeat(paramType.getDimensions()) + elementType.getDescriptor();
            }
        }
        super.visitTypeInsn(opcode, type);
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
            Constants.INVOCATION_CONSTRUCTOR_WITH_INSTANCE.toMethodInsn(getDelegate(), false);
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
            Constants.INVOCATION_CONSTRUCTOR.toMethodInsn(getDelegate(), false);
        }
        // load parameter with opcode (ALOAD, ILOAD, etc.) for type and ignore "this", if it exists
        for (int i = 0; i < argumentTypes.length; i++) {
            super.visitInsn(DUP);
            super.visitVarInsn(argumentTypes[i].getOpcode(ILOAD), getLocalsIndex(argumentTypes, i) + (isStatic ? 0 : 1));
            boxType(getDelegate(), argumentTypes[i]);
            Constants.INVOCATION_CONSTRUCTOR_ADD_PARAMETER.toMethodInsn(getDelegate(), false);
        }
    }

    private void buildConstructorInvocationBranch(MethodHeader constructorHeader,
                                                  Label substitutionExecuteLabel,
                                                  Label[] labels,
                                                  AtomicInteger labelIndex) {
        super.visitVarInsn(ALOAD, constructorInvocationIndex);
        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_OWNER.toMethodInsn(getDelegate(), false);
        super.visitLdcInsn(constructorHeader.owner());
        super.visitMethodInsn(INVOKEVIRTUAL,
            Constants.STRING_TYPE.getInternalName(),
            "equals",
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Constants.OBJECT_TYPE),
            false);

        super.visitVarInsn(ALOAD, constructorInvocationIndex);
        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_DESCRIPTOR.toMethodInsn(getDelegate(), false);
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
        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_ARGS.toMethodInsn(getDelegate(), false);
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
        constructorHeader.toMethodInsn(getDelegate(), false);
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

    // Prevent bytecode to be added to the method if there is a header mismatch

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        if (!headerMismatch) {
            Object[] computedLocals = Arrays.stream(local)
                .map(o -> {
                    if (o instanceof String s && transformationContext.isSubmissionClass(s)) {
                        return TransformationUtils.getComputedName(transformationContext, s);
                    } else {
                        return o;
                    }
                })
                .toArray();
            Object[] computedStack = Arrays.stream(stack)
                .map(o -> {
                    if (o instanceof String s && transformationContext.isSubmissionClass(s)) {
                        return TransformationUtils.getComputedName(transformationContext, s);
                    } else {
                        return o;
                    }
                })
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
        if (!headerMismatch) {
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }
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
        if (!headerMismatch) {
            super.visitMultiANewArrayInsn(descriptor, numDimensions);
        }
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
        if (!headerMismatch) {
            super.visitTryCatchBlock(start, end, handler, type);
        }
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return headerMismatch ? null : super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        if (!headerMismatch) {
            super.visitLocalVariable(name, descriptor, signature, start, end, index);
        }
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
