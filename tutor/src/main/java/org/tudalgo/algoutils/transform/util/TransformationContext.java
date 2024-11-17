package org.tudalgo.algoutils.transform.util;

import org.tudalgo.algoutils.transform.SolutionClassNode;
import org.tudalgo.algoutils.transform.SolutionMergingClassTransformer;
import org.tudalgo.algoutils.transform.SubmissionClassInfo;

import java.util.Map;

/**
 * A record for holding context information for the transformation process.
 *
 * @param configuration     configuration for this transformer run
 * @param solutionClasses   a mapping of solution class names to their respective {@link SolutionClassNode}
 * @param submissionClasses a mapping of submission class names to their respective {@link SubmissionClassInfo}
 * @author Daniel Mangold
 */
public record TransformationContext(
    Map<SolutionMergingClassTransformer.Config, Object> configuration,
    Map<String, SolutionClassNode> solutionClasses,
    Map<String, SubmissionClassInfo> submissionClasses
) {

    /**
     * Returns the project prefix.
     *
     * @return the project prefix
     */
    public String getProjectPrefix() {
        return (String) configuration.get(SolutionMergingClassTransformer.Config.PROJECT_PREFIX);
    }

    /**
     * Returns the minimum similarity threshold.
     *
     * @return the minimum similarity threshold
     */
    public double getSimilarity() {
        return (Double) configuration.get(SolutionMergingClassTransformer.Config.SIMILARITY);
    }

    /**
     * Returns the {@link SubmissionClassInfo} for a given submission class name.
     * If no mapping exists in {@link #submissionClasses}, will attempt to compute one.
     *
     * @param submissionClassName the submission class name
     * @return the {@link SubmissionClassInfo} object
     */
    public SubmissionClassInfo getSubmissionClassInfo(String submissionClassName) {
        return submissionClasses.computeIfAbsent(submissionClassName,
            className -> TransformationUtils.readSubmissionClass(this, className));
    }
}
