package org.tudalgo.algoutils.transform.methods;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.tudalgo.algoutils.transform.classes.SubmissionClassInfo;
import org.tudalgo.algoutils.transform.util.Constants;
import org.tudalgo.algoutils.transform.util.TransformationContext;
import org.tudalgo.algoutils.transform.util.TransformationUtils;
import org.tudalgo.algoutils.transform.util.headers.FieldHeader;
import org.tudalgo.algoutils.transform.util.headers.MethodHeader;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

public class ClassInitVisitor extends BaseMethodVisitor {

    private final Set<FieldHeader> solutionFields;
    private final MethodNode solutionMethodNode;

    public ClassInitVisitor(MethodVisitor delegate,
                            TransformationContext transformationContext,
                            SubmissionClassInfo submissionClassInfo) {
        super(delegate,
            transformationContext,
            submissionClassInfo,
            getClassInitHeader(submissionClassInfo.getOriginalClassHeader().name()),
            getClassInitHeader(submissionClassInfo.getComputedClassHeader().name()));

        this.solutionFields = submissionClassInfo.getSolutionClass()
            .map(solutionClassNode -> solutionClassNode.getFields().keySet())
            .orElse(Collections.emptySet());
        this.solutionMethodNode = submissionClassInfo.getSolutionClass()
            .map(solutionClassNode -> solutionClassNode.getMethods().get(computedMethodHeader))
            .orElse(null);
    }

    // Unused
    @Override
    protected int getLocalsIndex(LocalsObject localsObject) {
        return 0;
    }

    @Override
    public void visitCode() {
        FieldHeader fieldHeader = Constants.INJECTED_ORIGINAL_STATIC_FIELD_VALUES;
        Type hashMapType = Type.getType(HashMap.class);

        delegate.visitTypeInsn(NEW, hashMapType.getInternalName());
        delegate.visitInsn(DUP);
        delegate.visitMethodInsn(INVOKESPECIAL, hashMapType.getInternalName(), "<init>", "()V", false);
        delegate.visitFieldInsn(PUTSTATIC, computedMethodHeader.owner(), fieldHeader.name(), fieldHeader.descriptor());
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (opcode == PUTSTATIC && owner.equals(originalMethodHeader.owner())) {
            FieldHeader originalStaticFieldValuesHeader = Constants.INJECTED_ORIGINAL_STATIC_FIELD_VALUES;
            FieldHeader computedFieldHeader = classInfo.getComputedFieldHeader(name);
            Type type = Type.getType(computedFieldHeader.descriptor());
            boolean isCategory2Type = TransformationUtils.isCategory2Type(type);

            delegate.visitInsn(isCategory2Type ? DUP2 : DUP);
            delegate.visitFieldInsn(GETSTATIC,
                computedMethodHeader.owner(),
                originalStaticFieldValuesHeader.name(),
                originalStaticFieldValuesHeader.descriptor());
            delegate.visitInsn(isCategory2Type ? DUP_X2 : DUP_X1);
            delegate.visitInsn(POP);
            delegate.visitLdcInsn(computedFieldHeader.name());
            delegate.visitInsn(isCategory2Type ? DUP_X2 : DUP_X1);
            delegate.visitInsn(POP);
            TransformationUtils.boxType(delegate, type);
            delegate.visitMethodInsn(INVOKEINTERFACE,
                Constants.MAP_TYPE.getInternalName(),
                "put",
                Type.getMethodDescriptor(Constants.OBJECT_TYPE, Constants.OBJECT_TYPE, Constants.OBJECT_TYPE),
                true);
            delegate.visitInsn(POP);

            if (solutionFields.contains(computedFieldHeader)) {
                delegate.visitInsn(isCategory2Type ? POP2 : POP);
                return;
            }
        }
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode != RETURN || solutionMethodNode == null) super.visitInsn(opcode);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        if (solutionMethodNode == null) super.visitMaxs(maxStack, maxLocals);
    }

    @Override
    public void visitEnd() {
        if (solutionMethodNode != null) {
            solutionMethodNode.accept(delegate);
        }
    }

    private static MethodHeader getClassInitHeader(String owner) {
        return new MethodHeader(owner, ACC_STATIC, "<clinit>", "()V", null, null);
    }
}
