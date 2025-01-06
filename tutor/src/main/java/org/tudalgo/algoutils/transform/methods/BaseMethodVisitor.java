package org.tudalgo.algoutils.transform.methods;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.MethodNode;
import org.tudalgo.algoutils.transform.classes.ClassInfo;
import org.tudalgo.algoutils.transform.classes.SubmissionClassInfo;
import org.tudalgo.algoutils.transform.util.*;
import org.tudalgo.algoutils.transform.util.headers.FieldHeader;
import org.tudalgo.algoutils.transform.util.headers.MethodHeader;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;
import static org.tudalgo.algoutils.transform.util.TransformationUtils.boxType;
import static org.tudalgo.algoutils.transform.util.TransformationUtils.unboxType;

public abstract class BaseMethodVisitor extends MethodVisitor {

    protected final MethodVisitor delegate;
    protected final TransformationContext transformationContext;
    protected final ClassInfo classInfo;
    protected final MethodHeader originalMethodHeader;
    protected final MethodHeader computedMethodHeader;

    protected final boolean headerMismatch;
    protected final boolean isStatic;
    protected final boolean isConstructor;

    protected final int nextLocalsIndex;
    protected final List<Object> fullFrameLocals;

    protected BaseMethodVisitor(MethodVisitor delegate,
                                TransformationContext transformationContext,
                                ClassInfo classInfo,
                                MethodHeader originalMethodHeader,
                                MethodHeader computedMethodHeader) {
        super(ASM9, delegate);
        this.delegate = delegate;
        this.transformationContext = transformationContext;
        this.classInfo = classInfo;
        this.originalMethodHeader = originalMethodHeader;
        this.computedMethodHeader = computedMethodHeader;

        // Prevent bytecode to be added to the method if there is a header mismatch
        this.headerMismatch = !transformationContext.descriptorIsCompatible(originalMethodHeader.descriptor(),
            computedMethodHeader.descriptor());
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
    }

    public enum LocalsObject {
        METHOD_HEADER("methodHeader", Constants.METHOD_HEADER_TYPE.getDescriptor()),
        METHOD_SUBSTITUTION("methodSubstitution", Constants.METHOD_SUBSTITUTION_TYPE.getDescriptor()),
        CONSTRUCTOR_INVOCATION("constructorInvocation", Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getDescriptor());

        private final String varName;
        private final String descriptor;

        LocalsObject(String varName, String descriptor) {
            this.varName = varName;
            this.descriptor = descriptor;
        }

        public String varName() {
            return varName;
        }

        public String descriptor() {
            return descriptor;
        }

        public void visitLocalVariable(BaseMethodVisitor bmv, Label start, Label end) {
            bmv.visitLocalVariable(varName, descriptor, null, start, end, bmv.getLocalsIndex(this));
        }
    }

    protected abstract int getLocalsIndex(LocalsObject localsObject);

    protected void injectSetupCode(Label methodHeaderVarLabel) {
        // replicate method header in bytecode and store in locals array
        computedMethodHeader.buildHeader(delegate);
        delegate.visitVarInsn(ASTORE, getLocalsIndex(LocalsObject.METHOD_HEADER));
        delegate.visitLabel(methodHeaderVarLabel);

        delegate.visitFrame(F_APPEND, 1, new Object[] {computedMethodHeader.getHeaderType().getInternalName()}, 0, null);
        fullFrameLocals.add(computedMethodHeader.getHeaderType().getInternalName());
    }

    protected void injectInvocationLoggingCode(Label nextLabel) {
        // check if invocation should be logged
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_HEADER));
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_LOG_INVOCATION.toMethodInsn(delegate, false);
        delegate.visitJumpInsn(IFEQ, nextLabel); // jump to label if logInvocation(...) == false

        // intercept parameters
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_HEADER));
        injectInvocation(Type.getArgumentTypes(computedMethodHeader.descriptor()), true);
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_ADD_INVOCATION.toMethodInsn(delegate, false);
    }

    protected void injectSubstitutionCode(Label substitutionCheckLabel, Label nextLabel) {
        Label substitutionStartLabel = new Label();
        Label substitutionEndLabel = new Label();

        // check if substitution exists for this method
        delegate.visitFrame(F_SAME, 0, null, 0, null);
        delegate.visitLabel(substitutionCheckLabel);
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_HEADER));
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBSTITUTION.toMethodInsn(delegate, false);
        delegate.visitJumpInsn(IFEQ, nextLabel); // jump to label if useSubstitution(...) == false

        // get substitution and execute it
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_HEADER));
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_GET_SUBSTITUTION.toMethodInsn(delegate, false);
        delegate.visitVarInsn(ASTORE, getLocalsIndex(LocalsObject.METHOD_SUBSTITUTION));
        delegate.visitFrame(F_APPEND, 1, new Object[] {Constants.METHOD_SUBSTITUTION_TYPE.getInternalName()}, 0, null);
        fullFrameLocals.add(Constants.METHOD_SUBSTITUTION_TYPE.getInternalName());
        delegate.visitLabel(substitutionStartLabel);

        if (isConstructor) {
            List<MethodHeader> superConstructors = classInfo.getOriginalSuperClassConstructorHeaders()
                .stream()
                .map(mh -> classInfo.getComputedSuperClassConstructorHeader(mh.descriptor()))
                .toList();
            List<MethodHeader> constructors = classInfo.getOriginalMethodHeaders()
                .stream()
                .filter(mh -> mh.name().equals("<init>"))
                .map(mh -> classInfo.getComputedMethodHeader(mh.name(), mh.descriptor()))
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
            delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_SUBSTITUTION));
            Constants.METHOD_SUBSTITUTION_GET_CONSTRUCTOR_INVOCATION.toMethodInsn(delegate, true);
            delegate.visitVarInsn(ASTORE, getLocalsIndex(LocalsObject.CONSTRUCTOR_INVOCATION));
            delegate.visitFrame(F_APPEND, 1, new Object[] {Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName()}, 0, null);
            fullFrameLocals.add(Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_TYPE.getInternalName());
            delegate.visitLabel(labels[0]);
            for (MethodHeader superConstructorHeader : superConstructors) {
                injectConstructorInvocationBranch(superConstructorHeader, substitutionExecuteLabel, labels, labelIndex);
            }
            for (MethodHeader constructorHeader : constructors) {
                injectConstructorInvocationBranch(constructorHeader, substitutionExecuteLabel, labels, labelIndex);
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
                delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.CONSTRUCTOR_INVOCATION));
                Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_OWNER.toMethodInsn(delegate, false);
                delegate.visitInsn(AASTORE);
                delegate.visitInsn(DUP);
                delegate.visitInsn(ICONST_1);
                delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.CONSTRUCTOR_INVOCATION));
                Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_DESCRIPTOR.toMethodInsn(delegate, false);
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
            LocalsObject.CONSTRUCTOR_INVOCATION.visitLocalVariable(this, labels[labelIndex.get()], substitutionExecuteLabel);
        }

        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_SUBSTITUTION));
        injectInvocation(Type.getArgumentTypes(computedMethodHeader.descriptor()), true);
        Constants.METHOD_SUBSTITUTION_EXECUTE.toMethodInsn(delegate, true);
        Type returnType = Type.getReturnType(computedMethodHeader.descriptor());
        unboxType(delegate, returnType);
        delegate.visitInsn(returnType.getOpcode(IRETURN));
        delegate.visitLabel(substitutionEndLabel);
        LocalsObject.METHOD_SUBSTITUTION.visitLocalVariable(this, substitutionStartLabel, substitutionEndLabel);
        fullFrameLocals.removeLast();
    }

    protected void injectDelegationCode(MethodNode solutionMethodNode,
                                        Label delegationCheckLabel,
                                        Label submissionCodeLabel,
                                        Label methodHeaderVarLabel) {
        Label delegationCodeLabel = new Label();

        // check if call should be delegated to solution or not
        delegate.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
        delegate.visitLabel(delegationCheckLabel);
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_HEADER));
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBMISSION_IMPL.toMethodInsn(delegate, false);
        delegate.visitJumpInsn(IFNE, submissionCodeLabel); // jump to label if useSubmissionImpl(...) == true

        // replay instructions from solution
        delegate.visitFrame(F_CHOP, 1, null, 0, null);
        fullFrameLocals.removeLast();
        delegate.visitLabel(delegationCodeLabel);
        LocalsObject.METHOD_HEADER.visitLocalVariable(this, methodHeaderVarLabel, delegationCodeLabel);
        solutionMethodNode.accept(delegate);

        delegate.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
        delegate.visitLabel(submissionCodeLabel);
    }

    protected void injectNoDelegationCode(Label submissionCodeLabel, Label methodHeaderVarLabel) {
        fullFrameLocals.removeLast();
        delegate.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
        delegate.visitLabel(submissionCodeLabel);
        LocalsObject.METHOD_HEADER.visitLocalVariable(this, methodHeaderVarLabel, submissionCodeLabel);
    }

    /**
     * Builds an {@link Invocation} in bytecode.
     *
     * @param argumentTypes an array of parameter types
     * @param useLocals     whether to use the locals array or the stack
     */
    protected void injectInvocation(Type[] argumentTypes, boolean useLocals) {
        Type threadType = Type.getType(Thread.class);
        Type stackTraceElementArrayType = Type.getType(StackTraceElement[].class);

        delegate.visitTypeInsn(NEW, Constants.INVOCATION_TYPE.getInternalName());
        delegate.visitInsn(DUP);
        delegate.visitLdcInsn(Type.getObjectType(computedMethodHeader.owner()));
        computedMethodHeader.buildHeader(delegate);
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
            Constants.INVOCATION_CONSTRUCTOR_WITH_INSTANCE.toMethodInsn(delegate, false);
        } else {
            Constants.INVOCATION_CONSTRUCTOR.toMethodInsn(delegate, false);
        }
        if (useLocals) {
            // load parameter with opcode (ALOAD, ILOAD, etc.) for type and ignore "this", if it exists
            for (int i = 0; i < argumentTypes.length; i++) {
                delegate.visitInsn(DUP);
                delegate.visitVarInsn(argumentTypes[i].getOpcode(ILOAD), TransformationUtils.getLocalsIndex(argumentTypes, i) + (isStatic ? 0 : 1));
                boxType(delegate, argumentTypes[i]);
                Constants.INVOCATION_CONSTRUCTOR_ADD_PARAMETER.toMethodInsn(delegate, false);
            }
        } else {
            Label invocationStart = new Label();
            Label invocationEnd = new Label();
            Label[] paramStartLabels = Stream.generate(Label::new).limit(argumentTypes.length).toArray(Label[]::new);
            Label[] paramEndLabels = Stream.generate(Label::new).limit(argumentTypes.length).toArray(Label[]::new);
            Map<Integer, Integer> localsIndexes = new HashMap<>();

            delegate.visitVarInsn(ASTORE, fullFrameLocals.size());
            delegate.visitLabel(invocationStart);
            for (int i = argumentTypes.length - 1, category2Types = 0; i >= 0; i--) {
                Type argType = argumentTypes[i];
                localsIndexes.put(i, fullFrameLocals.size() + category2Types + argumentTypes.length - i);
                delegate.visitVarInsn(argType.getOpcode(ISTORE), localsIndexes.get(i));
                delegate.visitLabel(paramStartLabels[i]);
                if (TransformationUtils.isCategory2Type(argType)) category2Types++;
            }

            for (int i = 0; i < argumentTypes.length; i++) {
                Type argType = argumentTypes[i];

                delegate.visitVarInsn(ALOAD, fullFrameLocals.size());
                delegate.visitVarInsn(argType.getOpcode(ILOAD), localsIndexes.get(i));
                delegate.visitInsn(TransformationUtils.isCategory2Type(argType) ? DUP2_X1 : DUP_X1);
                boxType(delegate, argType);
                Constants.INVOCATION_CONSTRUCTOR_ADD_PARAMETER.toMethodInsn(delegate, false);
                delegate.visitLabel(paramEndLabels[i]);
                delegate.visitLocalVariable("var%d$injected".formatted(i),
                    argType.getDescriptor(),
                    null,
                    paramStartLabels[i],
                    paramEndLabels[i],
                    localsIndexes.get(i));
            }
            delegate.visitVarInsn(ALOAD, fullFrameLocals.size());
            delegate.visitLabel(invocationEnd);
            delegate.visitLocalVariable("invocation$injected",
                Constants.INVOCATION_TYPE.getDescriptor(),
                null,
                invocationStart,
                invocationEnd,
                fullFrameLocals.size());
        }
    }

    protected void injectConstructorInvocationBranch(MethodHeader constructorHeader,
                                                     Label substitutionExecuteLabel,
                                                     Label[] labels,
                                                     AtomicInteger labelIndex) {
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.CONSTRUCTOR_INVOCATION));
        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_OWNER.toMethodInsn(delegate, false);
        delegate.visitLdcInsn(constructorHeader.owner());
        delegate.visitMethodInsn(INVOKEVIRTUAL,
            Constants.STRING_TYPE.getInternalName(),
            "equals",
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Constants.OBJECT_TYPE),
            false);

        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.CONSTRUCTOR_INVOCATION));
        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_DESCRIPTOR.toMethodInsn(delegate, false);
        delegate.visitLdcInsn(constructorHeader.descriptor());
        delegate.visitMethodInsn(INVOKEVIRTUAL,
            Constants.STRING_TYPE.getInternalName(),
            "equals",
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Constants.OBJECT_TYPE),
            false);

        delegate.visitInsn(IAND);
        delegate.visitJumpInsn(IFEQ, labels[labelIndex.get() + 1]);  // jump to next branch if false

        Label argsVarStartLabel = new Label();
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.CONSTRUCTOR_INVOCATION));
        Constants.METHOD_SUBSTITUTION_CONSTRUCTOR_INVOCATION_ARGS.toMethodInsn(delegate, false);
        delegate.visitVarInsn(ASTORE, getLocalsIndex(LocalsObject.CONSTRUCTOR_INVOCATION) + 1);
        delegate.visitFrame(F_APPEND, 1, new Object[] {Constants.OBJECT_ARRAY_TYPE.getInternalName()}, 0, null);
        fullFrameLocals.add(Constants.OBJECT_ARRAY_TYPE.getInternalName());
        delegate.visitLabel(argsVarStartLabel);

        delegate.visitVarInsn(ALOAD, 0);
        Type[] parameterTypes = Type.getArgumentTypes(constructorHeader.descriptor());
        for (int i = 0; i < parameterTypes.length; i++) {  // unpack array
            delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.CONSTRUCTOR_INVOCATION) + 1);
            delegate.visitIntInsn(SIPUSH, i);
            delegate.visitInsn(AALOAD);
            unboxType(delegate, parameterTypes[i]);
        }
        constructorHeader.toMethodInsn(delegate, false);
        delegate.visitJumpInsn(GOTO, substitutionExecuteLabel);

        fullFrameLocals.removeLast();
        delegate.visitFrame(F_CHOP, 1, null, 0, new Object[0]);
        delegate.visitLabel(labels[labelIndex.incrementAndGet()]);
        delegate.visitLocalVariable("args",
            Constants.OBJECT_ARRAY_TYPE.getDescriptor(),
            null,
            argsVarStartLabel,
            labels[labelIndex.get()],
            getLocalsIndex(LocalsObject.CONSTRUCTOR_INVOCATION) + 1);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (headerMismatch) return;

        // skip transformation if owner is not part of the submission
        if (!transformationContext.isSubmissionClass(owner)) {
            delegate.visitFieldInsn(opcode, owner, name, descriptor);
        } else {
            FieldHeader computedFieldHeader = transformationContext.getSubmissionClassInfo(owner).getComputedFieldHeader(name);
            if (TransformationUtils.opcodeIsCompatible(opcode, computedFieldHeader.access()) &&
                transformationContext.descriptorIsCompatible(descriptor, computedFieldHeader.descriptor())) {
                delegate.visitFieldInsn(opcode,
                    transformationContext.toComputedInternalName(computedFieldHeader.owner()),
                    computedFieldHeader.name(),
                    computedFieldHeader.descriptor());
            } else { // if incompatible
                delegate.visitFieldInsn(opcode,
                    computedFieldHeader.owner(),
                    name + "$submission",
                    transformationContext.toComputedDescriptor(descriptor));
            }
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (headerMismatch) return;

        MethodHeader methodHeader = new MethodHeader(owner, name, descriptor);
        if (transformationContext.methodHasReplacement(methodHeader)) {
            transformationContext.getMethodReplacement(methodHeader).toMethodInsn(delegate, false);
        } else if (!transformationContext.isSubmissionClass(owner)) {
            delegate.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        } else if (owner.startsWith("[")) {
            delegate.visitMethodInsn(opcode, transformationContext.toComputedInternalName(owner), name, descriptor, isInterface);
        } else {
            // methodHeader.owner() might have the wrong owner for inherited methods
            String computedOwner = transformationContext.toComputedInternalName(owner);
            methodHeader = transformationContext.getSubmissionClassInfo(owner).getComputedMethodHeader(name, descriptor);
            if (TransformationUtils.opcodeIsCompatible(opcode, methodHeader.access()) &&
                transformationContext.descriptorIsCompatible(descriptor, methodHeader.descriptor())) {
                delegate.visitMethodInsn(opcode, computedOwner, methodHeader.name(), methodHeader.descriptor(), isInterface);
            } else {
                delegate.visitMethodInsn(opcode,
                    computedOwner,
                    name + "$submission",
                    transformationContext.toComputedDescriptor(descriptor),
                    isInterface);
            }
        }
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (headerMismatch) return;

        super.visitLdcInsn(value instanceof Type type ? transformationContext.toComputedType(type) : value);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (headerMismatch) return;

        super.visitTypeInsn(opcode, transformationContext.toComputedInternalName(type));
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        if (headerMismatch) return;

        Object[] computedLocals = local == null ? null : Arrays.stream(local)
            .map(o -> o instanceof String s ? transformationContext.toComputedInternalName(s) : o)
            .toArray();
        Object[] computedStack = stack == null ? null : Arrays.stream(stack)
            .map(o -> o instanceof String s ? transformationContext.toComputedInternalName(s) : o)
            .toArray();
        super.visitFrame(type, numLocal, computedLocals, numStack, computedStack);
    }

    @Override
    public void visitInsn(int opcode) {
        if (headerMismatch) return;

        super.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        if (headerMismatch) return;

        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        if (headerMismatch) return;

        super.visitIincInsn(varIndex, increment);
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
        if (headerMismatch) return;

        super.visitVarInsn(opcode, varIndex);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        if (headerMismatch) return;

        super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        if (headerMismatch) return;

        if (bootstrapMethodHandle.getOwner().equals("java/lang/invoke/LambdaMetafactory") &&
            bootstrapMethodHandle.getName().equals("metafactory")) {
            /*
             * Since this stuff is very confusing, some explanations...
             * name: the name of the interface method to implement
             * descriptor:
             *   arg types: types of the capture variables, i.e., variables that are used in the lambda expression
             *              but are not declared therein
             *   return type: the owner of the method that is implemented
             * bootstrapMethodHandle: a method handle for the lambda metafactory, see docs on LambdaMetafactory
             * bootstrapMethodArguments[0]: descriptor for the interface method that is implemented
             * bootstrapMethodArguments[1]: a method handle for the actual implementation, i.e.,
             *                              the actual lambda method or some other method when using method references
             * bootstrapMethodArguments[2]: the descriptor that should be enforced at invocation time,
             *                              not sure if it includes the capture variables
             */

            String interfaceOwner = Type.getReturnType(descriptor).getInternalName();
            if (transformationContext.isSubmissionClass(interfaceOwner)) {
                SubmissionClassInfo submissionClassInfo = transformationContext.getSubmissionClassInfo(interfaceOwner);
                MethodHeader methodHeader = submissionClassInfo.getComputedMethodHeader(name, ((Type) bootstrapMethodArguments[0]).getDescriptor());
                name = methodHeader.name();
                bootstrapMethodArguments[0] = Type.getMethodType(methodHeader.descriptor());
            }

            Handle implementation = (Handle) bootstrapMethodArguments[1];
            if (transformationContext.isSubmissionClass(implementation.getOwner())) {
                SubmissionClassInfo submissionClassInfo = transformationContext.getSubmissionClassInfo(implementation.getOwner());
                MethodHeader methodHeader = submissionClassInfo.getComputedMethodHeader(implementation.getName(), implementation.getDesc());
                bootstrapMethodArguments[1] = new Handle(implementation.getTag(),
                    methodHeader.owner(),
                    methodHeader.name(),
                    methodHeader.descriptor(),
                    implementation.isInterface());
            }

            bootstrapMethodArguments[2] = transformationContext.toComputedType((Type) bootstrapMethodArguments[2]);
        }

        super.visitInvokeDynamicInsn(name,
            transformationContext.toComputedDescriptor(descriptor),
            bootstrapMethodHandle,
            bootstrapMethodArguments);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        if (headerMismatch) return;

        super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        if (headerMismatch) return;

        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        if (headerMismatch) return;

        super.visitMultiANewArrayInsn(transformationContext.toComputedDescriptor(descriptor), numDimensions);
    }

    @Override
    public void visitLabel(Label label) {
        if (headerMismatch) return;

        super.visitLabel(label);
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return headerMismatch ? null : super.visitInsnAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (headerMismatch) return;

        super.visitTryCatchBlock(start, end, handler, type != null ? transformationContext.toComputedInternalName(type) : null);
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return headerMismatch ? null : super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        if (headerMismatch && Arrays.stream(LocalsObject.values()).map(LocalsObject::varName).noneMatch(name::equals))
            return;

        super.visitLocalVariable(name, transformationContext.toComputedType(Type.getType(descriptor)).getDescriptor(), signature, start, end, index);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
        return headerMismatch ? null : super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        if (headerMismatch) return;

        super.visitLineNumber(line, start);
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        if (headerMismatch) return;

        super.visitAttribute(attribute);
    }
}
