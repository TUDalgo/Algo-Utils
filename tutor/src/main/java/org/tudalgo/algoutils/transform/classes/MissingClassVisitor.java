package org.tudalgo.algoutils.transform.classes;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.tudalgo.algoutils.transform.methods.MissingMethodVisitor;
import org.tudalgo.algoutils.transform.util.Constants;
import org.tudalgo.algoutils.transform.util.IncompatibleHeaderException;
import org.tudalgo.algoutils.transform.util.MethodHeader;
import org.tudalgo.algoutils.transform.util.TransformationContext;

/**
 * A class visitor for visiting and transforming classes that are absent from the submission
 * but present in the solution.
 *
 * @author Daniel Mangold
 */
public class MissingClassVisitor extends ClassVisitor {

    private final TransformationContext transformationContext;
    private final SolutionClassNode solutionClassNode;

    /**
     * Constructs a new {@link MissingClassVisitor} instance.
     *
     * @param delegate              the class visitor to delegate to
     * @param transformationContext the transformation context
     * @param solutionClassNode     the solution class
     */
    public MissingClassVisitor(ClassVisitor delegate,
                               TransformationContext transformationContext,
                               SolutionClassNode solutionClassNode) {
        super(Opcodes.ASM9, delegate);

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
        int maxStack = IncompatibleHeaderException.replicateInBytecode(mv, true,
            "Class does not exist in submission or could not be matched", solutionClassNode.getClassHeader(), null);
        mv.visitMaxs(maxStack, 0);
    }
}
