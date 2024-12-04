package org.tudalgo.algoutils.transform.methods;

import org.objectweb.asm.MethodVisitor;
import org.tudalgo.algoutils.transform.classes.SubmissionClassInfo;
import org.tudalgo.algoutils.transform.util.MethodHeader;
import org.tudalgo.algoutils.transform.util.TransformationContext;

/**
 * A method visitor for lambda methods in submission classes.
 * This visitor does not inject any additional code, but it transforms the lambda's code
 * so it doesn't cause any linkage errors.
 */
public class LambdaMethodVisitor extends BaseMethodVisitor {

    /**
     * Constructs a new {@link LambdaMethodVisitor}.
     *
     * @param delegate              the method visitor to delegate to
     * @param transformationContext the transformation context
     * @param submissionClassInfo   information about the submission class this method belongs to
     * @param methodHeader          the (original) method header
     */
    public LambdaMethodVisitor(MethodVisitor delegate,
                               TransformationContext transformationContext,
                               SubmissionClassInfo submissionClassInfo,
                               MethodHeader methodHeader) {
        super(delegate, transformationContext, submissionClassInfo, methodHeader, methodHeader);
    }

    // Unused
    @Override
    protected int getLocalsIndex(LocalsObject localsObject) {
        return -1;
    }
}
