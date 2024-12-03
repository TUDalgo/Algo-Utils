package org.tudalgo.algoutils.transform.methods;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.MethodNode;
import org.tudalgo.algoutils.transform.SubmissionClassInfo;
import org.tudalgo.algoutils.transform.SubmissionClassVisitor;
import org.tudalgo.algoutils.transform.util.*;

import java.util.*;

/**
 * A method visitor for transforming submission methods.
 *
 * @see SubmissionClassVisitor
 * @author Daniel Mangold
 */
public class SubmissionMethodVisitor extends BaseMethodVisitor {

    private final Map<LocalsObject, Integer> localsIndexes = new EnumMap<>(LocalsObject.class);

    /**
     * Constructs a new {@link SubmissionMethodVisitor}.
     *
     * @param delegate              the method visitor to delegate to
     * @param transformationContext the transformation context
     * @param submissionClassInfo   information about the submission class this method belongs to
     * @param originalMethodHeader  the computed method header of this method
     */
    public SubmissionMethodVisitor(MethodVisitor delegate,
                            TransformationContext transformationContext,
                            SubmissionClassInfo submissionClassInfo,
                            MethodHeader originalMethodHeader,
                            MethodHeader computedMethodHeader) {
        super(delegate, transformationContext, submissionClassInfo, originalMethodHeader, computedMethodHeader);

        localsIndexes.put(LocalsObject.SUBMISSION_EXECUTION_HANDLER, nextLocalsIndex);
        localsIndexes.put(LocalsObject.METHOD_HEADER, nextLocalsIndex + 1);
        localsIndexes.put(LocalsObject.METHOD_SUBSTITUTION, nextLocalsIndex + 2);
        localsIndexes.put(LocalsObject.CONSTRUCTOR_INVOCATION, nextLocalsIndex + 3);
    }

    @Override
    protected int getLocalsIndex(LocalsObject localsObject) {
        return localsIndexes.get(localsObject);
    }

    @Override
    public void visitCode() {
        Optional<MethodNode> solutionMethodNode = submissionClassInfo.getSolutionClass()
            .map(solutionClassNode -> solutionClassNode.getMethods().get(computedMethodHeader));
        Label submissionExecutionHandlerVarLabel = new Label();
        Label methodHeaderVarLabel = new Label();
        Label substitutionCheckLabel = new Label();
        Label delegationCheckLabel = new Label();
        Label submissionCodeLabel = new Label();

        // Setup
        injectSetupCode(submissionExecutionHandlerVarLabel, methodHeaderVarLabel);

        // Invocation logging
        injectInvocationLoggingCode(substitutionCheckLabel);

        // Method substitution
        injectSubstitutionCode(substitutionCheckLabel, solutionMethodNode.isPresent() ? delegationCheckLabel : submissionCodeLabel);

        // Method delegation
        // if no solution method is present, skip delegation
        if (solutionMethodNode.isPresent()) {
            injectDelegationCode(solutionMethodNode.get(),
                delegationCheckLabel,
                submissionCodeLabel,
                submissionExecutionHandlerVarLabel,
                methodHeaderVarLabel);
        } else {
            injectNoDelegationCode(submissionCodeLabel, submissionExecutionHandlerVarLabel, methodHeaderVarLabel);
        }

        if (headerMismatch) {
            new IncompatibleHeaderException("Method has incorrect return or parameter types", computedMethodHeader, originalMethodHeader)
                .replicateInBytecode(getDelegate(), true);
        } else {
            // visit original code
            delegate.visitCode();
        }
    }
}
