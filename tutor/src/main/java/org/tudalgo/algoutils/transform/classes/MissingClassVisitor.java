package org.tudalgo.algoutils.transform.classes;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.tudalgo.algoutils.transform.util.Constants;
import org.tudalgo.algoutils.transform.util.MethodHeader;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;

public class MissingClassVisitor extends ClassVisitor {

    public MissingClassVisitor(ClassVisitor delegate) {
        super(ASM9, delegate);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        // TODO: transform to use with SubmissionExecutionHandler
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        injectMetadataMethod(Constants.INJECTED_GET_ORIGINAL_CLASS_HEADER);
        injectMetadataMethod(Constants.INJECTED_GET_ORIGINAL_FIELD_HEADERS);
        injectMetadataMethod(Constants.INJECTED_GET_ORIGINAL_METHODS_HEADERS);

        super.visitEnd();
    }

    private void injectMetadataMethod(MethodHeader methodHeader) {
        MethodVisitor mv = methodHeader.toMethodVisitor(getDelegate());
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 0);
    }
}
