package org.tudalgo.algoutils.transform.util;

import kotlin.Pair;
import org.tudalgo.algoutils.tutor.general.match.MatchingUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Computes the similarity for the cross product of two given collections.
 * This creates a mapping of values in the first collection to the best match
 * in the second collection, if any.
 *
 * @param <T> the type of the collection's elements
 */
public class SimilarityMapper<T> {

    private final List<? extends T> rowMapping;
    private final List<? extends T> columnMapping;
    private final double[][] similarityMatrix;
    private final Map<T, Pair<T, Double>> bestMatches = new HashMap<>();

    /**
     * Creates a new {@link SimilarityMapper} instance, allowing columns to have aliases.
     *
     * @param from                the values to map from (rows)
     * @param to                  the values to map to (columns), with the map's values being aliases of the key
     * @param similarityThreshold the minimum similarity two values need to have to be considered a match
     * @param mappingFunction     a function for mapping the collection's elements to strings
     */
    public SimilarityMapper(Collection<? extends T> from,
                            Map<T, Collection<? extends T>> to,
                            double similarityThreshold,
                            Function<? super T, String> mappingFunction) {
        this.rowMapping = new ArrayList<>(from);
        this.columnMapping = new ArrayList<>(to.keySet());
        this.similarityMatrix = new double[from.size()][to.size()];
        computeSimilarity(to, similarityThreshold, mappingFunction);
    }

    /**
     * Creates a new {@link SimilarityMapper} instance.
     *
     * @param from                the values to map from (rows)
     * @param to                  the values to map to (columns)
     * @param similarityThreshold the minimum similarity two values need to have to be considered a match
     * @param mappingFunction     a function for mapping the collection's elements to strings
     */
    public SimilarityMapper(Collection<? extends T> from,
                            Collection<? extends T> to,
                            double similarityThreshold,
                            Function<? super T, String> mappingFunction) {
        this.rowMapping = new ArrayList<>(from);
        this.columnMapping = new ArrayList<>(to);
        this.similarityMatrix = new double[from.size()][to.size()];
        computeSimilarity(to.stream().collect(Collectors.toMap(Function.identity(), t -> Collections.emptyList())),
            similarityThreshold,
            mappingFunction);
    }

    /**
     * Returns the best match for the given value, wrapped in an optional.
     *
     * @param t the value to find the best match for
     * @return an optional wrapping the best match
     */
    public Optional<T> getBestMatch(T t) {
        return Optional.ofNullable(bestMatches.get(t)).map(Pair::getFirst);
    }

    /**
     * Computes the similarity for each entry in the cross product of the two input collections.
     * Also extracts the best matches and stores them in {@link #bestMatches} for easy access.
     *
     * @param to                  a mapping of columns to their aliases
     * @param similarityThreshold the minimum similarity two values need to have to be considered a match
     * @param mappingFunction     a function for mapping the collection's elements to strings
     */
    private void computeSimilarity(Map<? extends T, Collection<? extends T>> to,
                                   double similarityThreshold,
                                   Function<? super T, String> mappingFunction) {
        for (int i = 0; i < similarityMatrix.length; i++) {
            String row = mappingFunction.apply(rowMapping.get(i));
            int bestMatchIndex = -1;
            double bestSimilarity = similarityThreshold;
            for (int j = 0; j < similarityMatrix[i].length; j++) {
                similarityMatrix[i][j] = Stream.concat(Stream.of(columnMapping.get(j)), to.get(columnMapping.get(j)).stream())
                    .map(mappingFunction)
                    .mapToDouble(value -> MatchingUtils.similarity(row, value))
                    .max()
                    .orElseThrow();
                if (similarityMatrix[i][j] >= bestSimilarity) {
                    bestMatchIndex = j;
                    bestSimilarity = similarityMatrix[i][j];
                }
            }
            if (bestMatchIndex >= 0) {
                Pair<T, Double> pair = new Pair<>(columnMapping.get(bestMatchIndex), bestSimilarity);
                bestMatches.merge(rowMapping.get(i), pair, (oldPair, newPair) ->
                    newPair.getSecond() > oldPair.getSecond() ? newPair : oldPair);
            }
        }

        // find and remove duplicate mappings
        Map<T, Stack<T>> reverseMapping = new HashMap<>();  // column => rows
        bestMatches.forEach((t, pair) -> reverseMapping.computeIfAbsent(pair.getFirst(), k -> new Stack<>()).push(t));
        reverseMapping.entrySet()
            .stream()
            .filter(entry -> entry.getValue().size() > 1)
            .forEach(entry -> {
                Stack<T> stack = entry.getValue();
                stack.sort(Comparator.comparingDouble(t -> bestMatches.get(t).getSecond()));
                stack.pop(); // exclude the best match from removal
                stack.forEach(bestMatches::remove); // remove the rest
            });
    }
}
