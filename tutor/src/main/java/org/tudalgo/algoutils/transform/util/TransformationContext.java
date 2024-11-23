package org.tudalgo.algoutils.transform.util;

import org.tudalgo.algoutils.transform.SolutionClassNode;
import org.tudalgo.algoutils.transform.SolutionMergingClassTransformer;
import org.tudalgo.algoutils.transform.SubmissionClassInfo;

import java.util.Map;
import java.util.Objects;

/**
 * A record for holding context information for the transformation process.
 *
 * @author Daniel Mangold
 */
public final class TransformationContext {

    private final Map<SolutionMergingClassTransformer.Config, Object> configuration;
    private final Map<String, SolutionClassNode> solutionClasses;
    private final Map<String, SubmissionClassInfo> submissionClasses;
    private ClassLoader submissionClassLoader;

    /**
     * Constructs a new {@link TransformationContext}.
     *
     * @param configuration     configuration for this transformer run
     * @param solutionClasses   a mapping of solution class names to their respective {@link SolutionClassNode}
     * @param submissionClasses a mapping of submission class names to their respective {@link SubmissionClassInfo}
     */
    public TransformationContext(
        Map<SolutionMergingClassTransformer.Config, Object> configuration,
        Map<String, SolutionClassNode> solutionClasses,
        Map<String, SubmissionClassInfo> submissionClasses
    ) {
        this.configuration = configuration;
        this.solutionClasses = solutionClasses;
        this.submissionClasses = submissionClasses;
    }

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
     * Whether the given method call should be replaced.
     *
     * @param methodHeader the header of the target method
     * @return true, if a replacement exists, otherwise false
     */
    public boolean methodHasReplacement(MethodHeader methodHeader) {
        return getMethodReplacement(methodHeader) != null;
    }

    public void setSubmissionClassLoader(ClassLoader submissionClassLoader) {
        this.submissionClassLoader = submissionClassLoader;
    }

    public ClassLoader getSubmissionClassLoader() {
        return submissionClassLoader;
    }

    /**
     * Returns the replacement method header for the given target method header.
     *
     * @param methodHeader the header of the target method
     * @return the replacement method header
     */
    @SuppressWarnings("unchecked")
    public MethodHeader getMethodReplacement(MethodHeader methodHeader) {
        return ((Map<MethodHeader, MethodHeader>) configuration.get(SolutionMergingClassTransformer.Config.METHOD_REPLACEMENTS))
            .get(methodHeader);
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

    public Map<SolutionMergingClassTransformer.Config, Object> configuration() {
        return configuration;
    }

    public Map<String, SolutionClassNode> solutionClasses() {
        return solutionClasses;
    }

    public Map<String, SubmissionClassInfo> submissionClasses() {
        return submissionClasses;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (TransformationContext) obj;
        return Objects.equals(this.configuration, that.configuration) &&
            Objects.equals(this.solutionClasses, that.solutionClasses) &&
            Objects.equals(this.submissionClasses, that.submissionClasses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configuration, solutionClasses, submissionClasses);
    }

    @Override
    public String toString() {
        return "TransformationContext[configuration=%s, solutionClasses=%s, submissionClasses=%s]"
            .formatted(configuration, solutionClasses, submissionClasses);
    }

}
