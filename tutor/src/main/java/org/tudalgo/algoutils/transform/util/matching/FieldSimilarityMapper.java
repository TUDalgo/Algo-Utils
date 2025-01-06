package org.tudalgo.algoutils.transform.util.matching;

import org.tudalgo.algoutils.transform.util.headers.FieldHeader;
import org.tudalgo.algoutils.transform.util.TransformationContext;
import org.tudalgo.algoutils.tutor.general.match.MatchingUtils;

import java.util.Collection;

public class FieldSimilarityMapper extends SimilarityMapper<FieldHeader> {

    /**
     * Creates a new {@link FieldSimilarityMapper} instance.
     *
     * @param submissionFields      the field headers to map from
     * @param solutionFields        the field headers to map to
     * @param transformationContext the transformation context
     */
    public FieldSimilarityMapper(Collection<? extends FieldHeader> submissionFields,
                                 Collection<? extends FieldHeader> solutionFields,
                                 TransformationContext transformationContext) {
        super(submissionFields, solutionFields, transformationContext.getSimilarity());

        computeSimilarity((submissionField, solutionField) ->
            MatchingUtils.similarity(submissionField.name(), solutionField.name()));
        removeDuplicateMappings();
    }
}
