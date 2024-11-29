package org.tudalgo.algoutils.transform.util;

import org.tudalgo.algoutils.tutor.general.match.MatchingUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class SimilarityMapper<T> {

    private final double[][] similarityMatrix;
    private final Map<T, T> bestMatches = new HashMap<>();

    @SuppressWarnings("unchecked")
    public SimilarityMapper(Collection<String> from, Map<String, Collection<String>> to, double similarityThreshold) {
        List<String> rowMapping = new ArrayList<>(from);
        List<String> columnMapping = new ArrayList<>(to.keySet());
        this.similarityMatrix = new double[from.size()][to.size()];

        for (int i = 0; i < similarityMatrix.length; i++) {
            String row = rowMapping.get(i);
            int bestMatchIndex = -1;
            double bestSimilarity = similarityThreshold;
            for (int j = 0; j < similarityMatrix[i].length; j++) {
                similarityMatrix[i][j] = Stream.concat(Stream.of(columnMapping.get(j)), to.get(columnMapping.get(j)).stream())
                    .mapToDouble(value -> MatchingUtils.similarity(row, value))
                    .max()
                    .orElseThrow();
                if (similarityMatrix[i][j] >= bestSimilarity) {
                    bestMatchIndex = j;
                    bestSimilarity = similarityMatrix[i][j];
                }
            }
            if (bestMatchIndex >= 0) {
                bestMatches.put((T) rowMapping.get(i), (T) columnMapping.get(bestMatchIndex));
            }
        }
    }

    public SimilarityMapper(Collection<? extends T> from,
                            Collection<? extends T> to,
                            double similarityThreshold,
                            Function<? super T, String> mappingFunction) {
        List<T> rowMapping = new ArrayList<>(from);
        List<T> columnMapping = new ArrayList<>(to);
        this.similarityMatrix = new double[from.size()][to.size()];

        for (int i = 0; i < similarityMatrix.length; i++) {
            String row = mappingFunction.apply(rowMapping.get(i));
            int bestMatchIndex = -1;
            double bestSimilarity = similarityThreshold;
            for (int j = 0; j < similarityMatrix[i].length; j++) {
                similarityMatrix[i][j] = MatchingUtils.similarity(row, mappingFunction.apply(columnMapping.get(j)));
                if (similarityMatrix[i][j] >= bestSimilarity) {
                    bestMatchIndex = j;
                    bestSimilarity = similarityMatrix[i][j];
                }
            }
            if (bestMatchIndex >= 0) {
                bestMatches.put(rowMapping.get(i), columnMapping.get(bestMatchIndex));
            }
        }
    }

    public Optional<T> getBestMatch(T t) {
        return Optional.ofNullable(bestMatches.get(t));
    }
}
