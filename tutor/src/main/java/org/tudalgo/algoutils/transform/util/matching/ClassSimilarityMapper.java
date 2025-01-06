package org.tudalgo.algoutils.transform.util.matching;

import org.tudalgo.algoutils.tutor.general.match.MatchingUtils;

import java.util.*;
import java.util.stream.Stream;

public class ClassSimilarityMapper extends SimilarityMapper<String> {

    /**
     * Creates a new {@link ClassSimilarityMapper} instance.
     *
     * @param submissionClassNames the submission classes to map from
     * @param solutionClassNames   the solution classes to map to, with the map's values being aliases of the key
     * @param similarityThreshold  the minimum similarity two values need to have to be considered a match
     */
    public ClassSimilarityMapper(Collection<String> submissionClassNames,
                                 Map<String, Collection<String>> solutionClassNames,
                                 double similarityThreshold) {
        super(submissionClassNames, solutionClassNames.keySet(), similarityThreshold);

        computeSimilarity((submissionClassName, solutionClassName) ->
            Stream.concat(Stream.of(solutionClassName), solutionClassNames.get(solutionClassName).stream())
                .mapToDouble(value -> MatchingUtils.similarity(submissionClassName, value))
                .max()
                .orElse(0));
        removeDuplicateMappings();
    }
}
