package org.tudalgo.algoutils.transform.util;

import org.tudalgo.algoutils.tutor.general.match.MatchingUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class SimilarityMapper<T> {

    private final Map<T, T> bestMatches = new HashMap<>();

    @SuppressWarnings("unchecked")
    public SimilarityMapper(Collection<String> from, Map<String, Collection<String>> to, double similarityThreshold) {
        List<String> rowMapping = new ArrayList<>(from);
        List<String> columnMapping = new ArrayList<>(to.keySet());
        double[][] similarityMatrix = new double[from.size()][to.size()];

        for (int i = 0; i < similarityMatrix.length; i++) {
            final int finalI = i;
            int bestMatchIndex = -1;
            double bestSimilarity = similarityThreshold;
            for (int j = 0; j < similarityMatrix[i].length; j++) {
                similarityMatrix[i][j] = Stream.concat(Stream.of(columnMapping.get(j)), to.get(columnMapping.get(j)).stream())
                    .mapToDouble(value -> MatchingUtils.similarity(rowMapping.get(finalI), value))
                    .max()
                    .getAsDouble();
                if (similarityMatrix[i][j] >= bestSimilarity) {
                    bestMatchIndex = j;
                    bestSimilarity = similarityMatrix[i][j];
                }
            }
            bestMatches.put((T) rowMapping.get(i), bestMatchIndex >= 0 ? (T) columnMapping.get(bestMatchIndex) : null);
        }
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
