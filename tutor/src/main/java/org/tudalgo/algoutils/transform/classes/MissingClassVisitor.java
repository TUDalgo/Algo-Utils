package org.tudalgo.algoutils.transform.classes;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.tudalgo.algoutils.transform.methods.MissingMethodVisitor;
import org.tudalgo.algoutils.transform.util.Constants;
import org.tudalgo.algoutils.transform.util.MethodHeader;
import org.tudalgo.algoutils.transform.util.TransformationContext;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;

public class MissingClassVisitor extends ClassVisitor {

    private final TransformationContext transformationContext;
    private final SolutionClassNode solutionClassNode;

    public MissingClassVisitor(ClassVisitor delegate,
                               TransformationContext transformationContext,
                               SolutionClassNode solutionClassNode) {
        super(ASM9, delegate);

        this.transformationContext = transformationContext;
        this.solutionClassNode = solutionClassNode;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodHeader methodHeader = new MethodHeader(solutionClassNode.getClassHeader().name(), access, name, descriptor, signature, exceptions);
        return new MissingMethodVisitor(methodHeader.toMethodVisitor(getDelegate()),
            transformationContext,
            new MissingClassInfo(transformationContext, solutionClassNode),
            methodHeader);
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
