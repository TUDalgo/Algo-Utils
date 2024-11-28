package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.Type;
import org.tudalgo.algoutils.transform.SolutionClassNode;
import org.tudalgo.algoutils.transform.SolutionMergingClassTransformer;
import org.tudalgo.algoutils.transform.SubmissionClassInfo;

import java.util.*;

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
    private Set<String> submissionClassNames;
    private SimilarityMapper<String> classSimilarityMapper;

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

    public void setSubmissionClassNames(Set<String> submissionClassNames) {
        this.submissionClassNames = submissionClassNames;
    }

    public boolean isSubmissionClass(String submissionClassName) {
        if (submissionClassName.startsWith("[")) {
            return isSubmissionClass(Type.getType(submissionClassName).getElementType().getInternalName());
        } else {
            return submissionClassNames.contains(submissionClassName);
        }
    }

    /**
     * Returns the {@link SubmissionClassInfo} for a given submission class name.
     * If no mapping exists in {@link #submissionClasses}, will attempt to compute one.
     *
     * @param submissionClassName the submission class name
     * @return the {@link SubmissionClassInfo} object
     */
    public SubmissionClassInfo getSubmissionClassInfo(String submissionClassName) {
        boolean isAbsent = !submissionClasses.containsKey(submissionClassName);
        SubmissionClassInfo submissionClassInfo = submissionClasses.computeIfAbsent(submissionClassName,
            className -> TransformationUtils.readSubmissionClass(this, className));
        if (isAbsent && submissionClassInfo != null) {
            submissionClassInfo.mapToSolutionClass();
        }
        return submissionClassInfo;
    }

    public Map<String, SolutionClassNode> solutionClasses() {
        return solutionClasses;
    }

    @SuppressWarnings("unchecked")
    public void computeClassesSimilarity() {
        classSimilarityMapper = new SimilarityMapper<>(submissionClassNames,
            (Map<String, Collection<String>>) configuration.get(SolutionMergingClassTransformer.Config.SOLUTION_CLASSES),
            getSimilarity());
    }

    public String getSolutionClassName(String submissionClassName) {
        return classSimilarityMapper.getBestMatch(submissionClassName);
    }

    public String getComputedName(String className) {
        if (isSubmissionClass(className)) {
            Type type = className.startsWith("[") ? Type.getType(className) : Type.getObjectType(className);
            if (type.getSort() == Type.OBJECT) {
                return getSubmissionClassInfo(className).getComputedClassName();
            } else {  // else must be array
                return "%sL%s;".formatted("[".repeat(type.getDimensions()),
                    getSubmissionClassInfo(type.getElementType().getInternalName()).getComputedClassName());
            }
        } else {
            return className;
        }
    }

    public Type getComputedType(Type type) {
        if (type.getSort() == Type.OBJECT) {
            return Type.getObjectType(getComputedName(type.getInternalName()));
        } else if (type.getSort() == Type.ARRAY) {
            return Type.getType(getComputedName(type.getDescriptor()));
        } else {
            return type;
        }
    }

    @Override
    public String toString() {
        return "TransformationContext[configuration=%s, solutionClasses=%s, submissionClasses=%s]"
            .formatted(configuration, solutionClasses, submissionClasses);
    }

}
