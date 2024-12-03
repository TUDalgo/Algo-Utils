package org.tudalgo.algoutils.transform.methods;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.MethodNode;
import org.tudalgo.algoutils.transform.classes.ClassInfo;
import org.tudalgo.algoutils.transform.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

        this.headerMismatch = !transformationContext.toComputedType(originalMethodHeader.descriptor())
            .getDescriptor()
            .equals(computedMethodHeader.descriptor());
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
        SUBMISSION_EXECUTION_HANDLER("submissionExecutionHandler", Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getDescriptor()),
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

        public void visitLocalVariable(BaseMethodVisitor mv, Label start, Label end) {
            mv.visitLocalVariable(varName, descriptor, null, start, end, mv.getLocalsIndex(this));
        }
    }

    protected abstract int getLocalsIndex(LocalsObject localsObject);

    protected void injectSetupCode(Label submissionExecutionHandlerVarLabel, Label methodHeaderVarLabel) {
        // create SubmissionExecutionHandler$Internal instance and store in locals array
        delegate.visitTypeInsn(NEW, Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName());
        delegate.visitInsn(DUP);
        Constants.SUBMISSION_EXECUTION_HANDLER_GET_INSTANCE.toMethodInsn(delegate, false);
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_CONSTRUCTOR.toMethodInsn(delegate, false);
        delegate.visitVarInsn(ASTORE, getLocalsIndex(LocalsObject.SUBMISSION_EXECUTION_HANDLER));
        delegate.visitLabel(submissionExecutionHandlerVarLabel);

        // replicate method header in bytecode and store in locals array
        computedMethodHeader.buildHeader(delegate);
        delegate.visitVarInsn(ASTORE, getLocalsIndex(LocalsObject.METHOD_HEADER));
        delegate.visitLabel(methodHeaderVarLabel);

        delegate.visitFrame(F_APPEND,
            2,
            new Object[] {Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName(), computedMethodHeader.getType().getInternalName()},
            0,
            null);
        fullFrameLocals.add(Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_TYPE.getInternalName());
        fullFrameLocals.add(computedMethodHeader.getType().getInternalName());
    }

    protected void injectInvocationLoggingCode(Label nextLabel) {
        // check if invocation should be logged
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.SUBMISSION_EXECUTION_HANDLER));
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_HEADER));
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_LOG_INVOCATION.toMethodInsn(delegate, false);
        delegate.visitJumpInsn(IFEQ, nextLabel); // jump to label if logInvocation(...) == false

        // intercept parameters
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.SUBMISSION_EXECUTION_HANDLER));
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_HEADER));
        injectInvocation(Type.getArgumentTypes(computedMethodHeader.descriptor()));
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_ADD_INVOCATION.toMethodInsn(delegate, false);
    }

    protected void injectSubstitutionCode(Label substitutionCheckLabel, Label nextLabel) {
        Label substitutionStartLabel = new Label();
        Label substitutionEndLabel = new Label();

        // check if substitution exists for this method
        delegate.visitFrame(F_SAME, 0, null, 0, null);
        delegate.visitLabel(substitutionCheckLabel);
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.SUBMISSION_EXECUTION_HANDLER));
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_HEADER));
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBSTITUTION.toMethodInsn(delegate, false);
        delegate.visitJumpInsn(IFEQ, nextLabel); // jump to label if useSubstitution(...) == false

        // get substitution and execute it
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.SUBMISSION_EXECUTION_HANDLER));
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
        injectInvocation(Type.getArgumentTypes(computedMethodHeader.descriptor()));
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
                                        Label submissionExecutionHandlerVarLabel,
                                        Label methodHeaderVarLabel) {
        Label delegationCodeLabel = new Label();

        // check if call should be delegated to solution or not
        delegate.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
        delegate.visitLabel(delegationCheckLabel);
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.SUBMISSION_EXECUTION_HANDLER));
        delegate.visitVarInsn(ALOAD, getLocalsIndex(LocalsObject.METHOD_HEADER));
        Constants.SUBMISSION_EXECUTION_HANDLER_INTERNAL_USE_SUBMISSION_IMPL.toMethodInsn(delegate, false);
        delegate.visitJumpInsn(IFNE, submissionCodeLabel); // jump to label if useSubmissionImpl(...) == true

        // replay instructions from solution
        delegate.visitFrame(F_CHOP, 2, null, 0, null);
        fullFrameLocals.removeLast();
        fullFrameLocals.removeLast();
        delegate.visitLabel(delegationCodeLabel);
        LocalsObject.SUBMISSION_EXECUTION_HANDLER.visitLocalVariable(this, submissionExecutionHandlerVarLabel, delegationCodeLabel);
        LocalsObject.METHOD_HEADER.visitLocalVariable(this, methodHeaderVarLabel, delegationCodeLabel);
        solutionMethodNode.accept(delegate);

        delegate.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
        delegate.visitLabel(submissionCodeLabel);
    }

    protected void injectNoDelegationCode(Label submissionCodeLabel,
                                          Label submissionExecutionHandlerVarLabel,
                                          Label methodHeaderVarLabel) {
        fullFrameLocals.removeLast();
        fullFrameLocals.removeLast();
        delegate.visitFrame(F_FULL, fullFrameLocals.size(), fullFrameLocals.toArray(), 0, new Object[0]);
        delegate.visitLabel(submissionCodeLabel);
        LocalsObject.SUBMISSION_EXECUTION_HANDLER.visitLocalVariable(this, submissionExecutionHandlerVarLabel, submissionCodeLabel);
        LocalsObject.METHOD_HEADER.visitLocalVariable(this, methodHeaderVarLabel, submissionCodeLabel);
    }

    /**
     * Builds an {@link Invocation} in bytecode.
     *
     * @param argumentTypes an array of parameter types
     */
    protected void injectInvocation(Type[] argumentTypes) {
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
        // load parameter with opcode (ALOAD, ILOAD, etc.) for type and ignore "this", if it exists
        for (int i = 0; i < argumentTypes.length; i++) {
            delegate.visitInsn(DUP);
            delegate.visitVarInsn(argumentTypes[i].getOpcode(ILOAD), TransformationUtils.getLocalsIndex(argumentTypes, i) + (isStatic ? 0 : 1));
            boxType(delegate, argumentTypes[i]);
            Constants.INVOCATION_CONSTRUCTOR_ADD_PARAMETER.toMethodInsn(delegate, false);
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

    // Prevent bytecode to be added to the method if there is a header mismatch

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
            transformationContext.getMethodReplacement(methodHeader).toMethodInsn(delegate, false);
        } else if (!transformationContext.isSubmissionClass(owner)) {
            delegate.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        } else if (owner.startsWith("[")) {  // stupid edge cases
            delegate.visitMethodInsn(opcode,
                transformationContext.toComputedType(owner).getInternalName(),
                name,
                transformationContext.toComputedType(descriptor).getDescriptor(),
                isInterface);
        } else {
            String computedOwner = transformationContext.toComputedType(owner).getInternalName();
            methodHeader = transformationContext.getSubmissionClassInfo(owner).getComputedMethodHeader(name, descriptor);
            if (TransformationUtils.opcodeIsCompatible(opcode, methodHeader.access())) {
                delegate.visitMethodInsn(opcode, computedOwner, methodHeader.name(), methodHeader.descriptor(), isInterface);
            } else {
                delegate.visitMethodInsn(opcode,
                    computedOwner,
                    name + "$submission",
                    transformationContext.toComputedType(descriptor).getDescriptor(),
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
