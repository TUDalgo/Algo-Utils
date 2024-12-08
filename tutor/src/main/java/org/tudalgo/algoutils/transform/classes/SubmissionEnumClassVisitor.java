package org.tudalgo.algoutils.transform.classes;

import org.objectweb.asm.*;
import org.tudalgo.algoutils.transform.methods.SubmissionMethodVisitor;
import org.tudalgo.algoutils.transform.util.Constants;
import org.tudalgo.algoutils.transform.util.TransformationContext;
import org.tudalgo.algoutils.transform.util.TransformationUtils;
import org.tudalgo.algoutils.transform.util.headers.FieldHeader;
import org.tudalgo.algoutils.transform.util.headers.MethodHeader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

public class SubmissionEnumClassVisitor extends SubmissionClassVisitor {

    private final MethodHeader classInitHeader = new MethodHeader(computedClassHeader.name(),
        ACC_STATIC, "<clinit>", "()V", null, null);
    private final Set<String> enumConstants = new HashSet<>();
    private final SolutionClassNode solutionClassNode;

    /**
     * Constructs a new {@link SubmissionEnumClassVisitor} instance.
     *
     * @param classVisitor          the class visitor to delegate to
     * @param transformationContext the transformation context
     * @param submissionClassName   the name of the submission class that is visited
     */
    public SubmissionEnumClassVisitor(ClassVisitor classVisitor,
                                      TransformationContext transformationContext,
                                      String submissionClassName) {
        super(classVisitor, transformationContext, submissionClassName);

        this.solutionClassNode = submissionClassInfo.getSolutionClass().orElse(null);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        Constants.INJECTED_ORIGINAL_ENUM_CONSTANTS.toFieldVisitor(getDelegate(), null);

        if (solutionClassNode != null) {
            solutionClassNode.getFields()
                .entrySet()
                .stream()
                .filter(entry -> (entry.getKey().access() & ACC_ENUM) != 0)
                .forEach(entry -> {
                    visitedFields.add(entry.getKey());
                    entry.getValue().accept(getDelegate());
                });
        }
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if ((access & ACC_ENUM) != 0) {
            enumConstants.add(name);
            return null;
        } else {
            return super.visitField(access, name, descriptor, signature, value);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals("<clinit>")) {
            visitedMethods.add(classInitHeader);
            return new ClassInitVisitor(getDelegate().visitMethod(access, name, descriptor, signature, exceptions));
        } else {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

    @Override
    public void visitEnd() {
        MethodVisitor mv = Constants.INJECTED_GET_ORIGINAL_ENUM_CONSTANTS.toMethodVisitor(getDelegate());
        mv.visitFieldInsn(GETSTATIC,
            computedClassHeader.name(),
            Constants.INJECTED_ORIGINAL_ENUM_CONSTANTS.name(),
            Constants.INJECTED_ORIGINAL_ENUM_CONSTANTS.descriptor());
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 0);

        super.visitEnd();
    }

    private class ClassInitVisitor extends SubmissionMethodVisitor {

        private ClassInitVisitor(MethodVisitor delegate) {
            super(delegate,
                SubmissionEnumClassVisitor.this.transformationContext,
                SubmissionEnumClassVisitor.this.submissionClassInfo,
                classInitHeader,
                classInitHeader);
        }

        @Override
        public void visitCode() {
            FieldHeader fieldHeader = Constants.INJECTED_ORIGINAL_ENUM_CONSTANTS;
            Type arrayListType = Type.getType(ArrayList.class);

            delegate.visitTypeInsn(NEW, arrayListType.getInternalName());
            delegate.visitInsn(DUP);
            delegate.visitMethodInsn(INVOKESPECIAL,
                arrayListType.getInternalName(),
                "<init>",
                "()V",
                false);
            delegate.visitFieldInsn(PUTSTATIC,
                computedClassHeader.name(),
                fieldHeader.name(),
                fieldHeader.descriptor());
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (!(owner.equals(originalClassHeader.name()) && (enumConstants.contains(name) || name.equals("$VALUES")))) {
                super.visitFieldInsn(opcode, owner, name, descriptor);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == INVOKESPECIAL && owner.equals(originalClassHeader.name()) && name.equals("<init>")) {
                Type[] argTypes = Type.getArgumentTypes(descriptor);
                Label invocationStart = new Label();
                Label invocationEnd = new Label();

                injectInvocation(argTypes, false);
                delegate.visitVarInsn(ASTORE, fullFrameLocals.size());
                delegate.visitLabel(invocationStart);
                delegate.visitTypeInsn(NEW, Constants.ENUM_CONSTANT_TYPE.getInternalName());
                delegate.visitInsn(DUP);

                delegate.visitVarInsn(ALOAD, fullFrameLocals.size());
                delegate.visitInsn(ICONST_0);
                delegate.visitMethodInsn(INVOKEVIRTUAL,
                    Constants.INVOCATION_TYPE.getInternalName(),
                    "getParameter",
                    Type.getMethodDescriptor(Constants.OBJECT_TYPE, Type.INT_TYPE),
                    false);
                delegate.visitTypeInsn(CHECKCAST, Constants.STRING_TYPE.getInternalName());

                delegate.visitVarInsn(ALOAD, fullFrameLocals.size());
                delegate.visitInsn(ICONST_1);
                delegate.visitMethodInsn(INVOKEVIRTUAL,
                    Constants.INVOCATION_TYPE.getInternalName(),
                    "getIntParameter",
                    Type.getMethodDescriptor(Type.INT_TYPE, Type.INT_TYPE),
                    false);

                delegate.visitIntInsn(SIPUSH, argTypes.length - 2);
                delegate.visitTypeInsn(ANEWARRAY, Constants.OBJECT_TYPE.getInternalName());
                for (int i = 2; i < argTypes.length; i++) {
                    delegate.visitInsn(DUP);
                    delegate.visitIntInsn(SIPUSH, i - 2);
                    delegate.visitVarInsn(ALOAD, fullFrameLocals.size());
                    delegate.visitIntInsn(SIPUSH, i);
                    delegate.visitMethodInsn(INVOKEVIRTUAL,
                        Constants.INVOCATION_TYPE.getInternalName(),
                        "getParameter",
                        Type.getMethodDescriptor(Constants.OBJECT_TYPE, Type.INT_TYPE),
                        false);
                    delegate.visitInsn(AASTORE);
                }

                delegate.visitMethodInsn(INVOKESPECIAL,
                    Constants.ENUM_CONSTANT_TYPE.getInternalName(),
                    "<init>",
                    Type.getMethodDescriptor(Type.VOID_TYPE, Constants.STRING_TYPE, Type.INT_TYPE, Constants.OBJECT_ARRAY_TYPE),
                    false);
                delegate.visitFieldInsn(GETSTATIC,
                    computedClassHeader.name(),
                    Constants.INJECTED_ORIGINAL_ENUM_CONSTANTS.name(),
                    Constants.INJECTED_ORIGINAL_ENUM_CONSTANTS.descriptor());
                delegate.visitInsn(SWAP);
                delegate.visitMethodInsn(INVOKEINTERFACE,
                    Constants.LIST_TYPE.getInternalName(),
                    "add",
                    Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Constants.OBJECT_TYPE),
                    true);
                delegate.visitInsn(POP);

                delegate.visitLabel(invocationEnd);
                delegate.visitLocalVariable("invocation$injected",
                    Constants.INVOCATION_TYPE.getDescriptor(),
                    null,
                    invocationStart,
                    invocationEnd,
                    fullFrameLocals.size());

                for (int i = argTypes.length - 1; i >= 0; i--) {
                    delegate.visitInsn(TransformationUtils.isCategory2Type(argTypes[i]) ? POP2 : POP);
                }
                delegate.visitInsn(POP2); // remove the new ref and its duplicate
            } else if (!(owner.equals(originalClassHeader.name()) && name.equals("$values"))) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode != RETURN || solutionClassNode == null) super.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            if (solutionClassNode == null) super.visitMaxs(maxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            if (solutionClassNode != null) {
                solutionClassNode.getMethods()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().name().equals("<clinit>"))
                    .findAny()
                    .map(Map.Entry::getValue)
                    .ifPresent(methodNode -> methodNode.accept(delegate));
            }

            super.visitEnd();
        }
    }
}
