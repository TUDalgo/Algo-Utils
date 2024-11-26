package org.tudalgo.algoutils.transform.util;

import org.tudalgo.algoutils.tutor.general.match.MatchingUtils;

import java.util.*;
import java.util.function.Function;

public class SimilarityMapper<T> {

    private final Map<T, T> bestMatches = new HashMap<>();

    @SuppressWarnings("unchecked")
    public SimilarityMapper(Collection<String> from, Collection<String> to, double similarityThreshold) {
        this((Collection<? extends T>) from, (Collection<? extends T>) to, similarityThreshold, s -> (String) s);
    }

    public SimilarityMapper(Collection<? extends T> from,
                            Collection<? extends T> to,
                            double similarityThreshold,
                            Function<? super T, String> mappingFunction) {
        List<T> rowMapping = new ArrayList<>(from);
        List<T> columnMapping = new ArrayList<>(to);
        double[][] similarityMatrix = new double[from.size()][to.size()];

        for (int i = 0; i < similarityMatrix.length; i++) {
            int bestMatchIndex = -1;
            double bestSimilarity = similarityThreshold;
            for (int j = 0; j < similarityMatrix[i].length; j++) {
                similarityMatrix[i][j] = MatchingUtils.similarity(mappingFunction.apply(rowMapping.get(i)),
                    mappingFunction.apply(columnMapping.get(j)));
                if (similarityMatrix[i][j] >= bestSimilarity) {
                    bestMatchIndex = j;
                    bestSimilarity = similarityMatrix[i][j];
                }
            }
            bestMatches.put(rowMapping.get(i), bestMatchIndex >= 0 ? columnMapping.get(bestMatchIndex) : null);
        }
    }

    public T getBestMatch(T t) {
        return bestMatches.get(t);
    }
}
