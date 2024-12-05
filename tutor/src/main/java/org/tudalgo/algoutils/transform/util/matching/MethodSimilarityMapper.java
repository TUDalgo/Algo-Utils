package org.tudalgo.algoutils.transform.util.matching;

import org.tudalgo.algoutils.transform.util.MethodHeader;
import org.tudalgo.algoutils.transform.util.TransformationContext;
import org.tudalgo.algoutils.tutor.general.match.MatchingUtils;

import java.util.*;

public class MethodSimilarityMapper extends SimilarityMapper<MethodHeader> {

    /**
     * Creates a new {@link MethodSimilarityMapper} instance.
     *
     * @param submissionMethods     the method headers to map from
     * @param solutionMethods       the method headers to map to
     * @param transformationContext the transformation context
     */
    public MethodSimilarityMapper(Collection<? extends MethodHeader> submissionMethods,
                                  Collection<? extends MethodHeader> solutionMethods,
                                  TransformationContext transformationContext) {
        super(submissionMethods, solutionMethods, transformationContext.getSimilarity());

        computeSimilarity((submissionMethod, solutionMethod) -> {
            String computedDescriptor = transformationContext.toComputedDescriptor(submissionMethod.descriptor());
            if (!computedDescriptor.equals(solutionMethod.descriptor())) {
                return 0d;
            } else {
                return MatchingUtils.similarity(submissionMethod.name() + computedDescriptor, solutionMethod.name() + solutionMethod.descriptor());
            }
        });
        removeDuplicateMappings();
    }
}
