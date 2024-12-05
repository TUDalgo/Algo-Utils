package org.tudalgo.algoutils.transform.util.matching;

import java.util.*;
import java.util.function.BiFunction;

public abstract class SimilarityMapper<T> {

    protected final List<? extends T> rows;
    protected final List<? extends T> columns;
    protected final double[][] similarityMatrix;
    protected final double similarityThreshold;
    protected final Map<T, T> bestMatches = new HashMap<>(); // row => column

    public SimilarityMapper(Collection<? extends T> rows, Collection<? extends T> columns, double similarityThreshold) {
        this.rows = new ArrayList<>(rows);
        this.columns = new ArrayList<>(columns);
        this.similarityMatrix = new double[rows.size()][columns.size()];
        this.similarityThreshold = similarityThreshold;
    }

    protected void computeSimilarity(BiFunction<? super T, ? super T, Double> similarityFunction) {
        for (int rowIndex = 0; rowIndex < similarityMatrix.length; rowIndex++) {
            T row = rows.get(rowIndex);
            int bestMatchIndex = -1;
            double bestSimilarity = similarityThreshold;

            for (int colIndex = 0; colIndex < similarityMatrix[rowIndex].length; colIndex++) {
                similarityMatrix[rowIndex][colIndex] = similarityFunction.apply(row, columns.get(colIndex));
                if (similarityMatrix[rowIndex][colIndex] >= bestSimilarity) {
                    bestMatchIndex = colIndex;
                    bestSimilarity = similarityMatrix[rowIndex][colIndex];
                }
            }
            if (bestMatchIndex >= 0) {
                bestMatches.put(row, columns.get(bestMatchIndex));
            }
        }
    }

    protected void removeDuplicateMappings() {
        Map<T, Stack<T>> reverseMappings = new HashMap<>();
        bestMatches.forEach((row, col) -> reverseMappings.computeIfAbsent(col, k -> new Stack<>()).add(row));
        reverseMappings.forEach((col, rows) -> {
            rows.sort(Comparator.comparingDouble(row -> similarityMatrix[rows.indexOf(row)][columns.indexOf(col)]));
            rows.pop(); // exclude best match
            rows.forEach(bestMatches::remove); // remove the rest
        });
    }

    /**
     * Returns the best match for the given value, wrapped in an optional.
     *
     * @param t the value to find the best match for
     * @return an optional wrapping the best match
     */
    public Optional<T> getBestMatch(T t) {
        return Optional.ofNullable(bestMatches.get(t));
    }
}
