package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.tudalgo.algoutils.transform.SolutionClassNode;
import org.tudalgo.algoutils.transform.SolutionMergingClassTransformer;
import org.tudalgo.algoutils.transform.SubmissionClassInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;

/**
 * A record for holding context information for the transformation process.
 *
 * @author Daniel Mangold
 */
public final class TransformationContext {

    private final Map<SolutionMergingClassTransformer.Config, Object> configuration;
    private final Map<String, SolutionClassNode> solutionClasses;
    private final Map<String, SubmissionClassInfo> submissionClasses;
    private final Set<String> visitedClasses = new HashSet<>();

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

    // Config and misc. stuff

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
     * Sets the class loader for submission classes.
     *
     * @param submissionClassLoader the class loader
     */
    public void setSubmissionClassLoader(ClassLoader submissionClassLoader) {
        this.submissionClassLoader = submissionClassLoader;
    }

    /**
     * Sets the available submission classes to the specified value.
     *
     * @param submissionClassNames the available submission classes
     */
    public void setSubmissionClassNames(Set<String> submissionClassNames) {
        this.submissionClassNames = submissionClassNames;
    }

    public void addVisitedClass(String className) {
        visitedClasses.add(className);
    }

    public Set<String> getVisitedClasses() {
        return Collections.unmodifiableSet(visitedClasses);
    }

    /**
     * Computes similarities for mapping submission classes to solution classes.
     */
    @SuppressWarnings("unchecked")
    public void computeClassesSimilarity() {
        classSimilarityMapper = new SimilarityMapper<>(submissionClassNames,
            (Map<String, Collection<? extends String>>) configuration.get(SolutionMergingClassTransformer.Config.SOLUTION_CLASSES),
            getSimilarity(),
            Function.identity());
    }

    // Submission classes

    /**
     * Whether the given class is a submission class.
     * The parameter must be either the internal name of a class or an array descriptor.
     *
     * @param submissionClassName the class name / array class descriptor
     * @return true, if the given class is a submission class, otherwise false
     */
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
        SubmissionClassInfo submissionClassInfo = submissionClasses.computeIfAbsent(submissionClassName, this::readSubmissionClass);
        if (isAbsent && submissionClassInfo != null) {
            submissionClassInfo.computeMembers();
        }
        return submissionClassInfo;
    }

    /**
     * Attempts to read and process a submission class.
     *
     * @param className the name of the submission class
     * @return the resulting {@link SubmissionClassInfo} object
     */
    public SubmissionClassInfo readSubmissionClass(String className) {
        ClassReader submissionClassReader;
        String submissionClassFilePath = className + ".class";
        try (InputStream is = submissionClassLoader.getResourceAsStream(submissionClassFilePath)) {
            if (is == null) {
                return null;
            }
            submissionClassReader = new ClassReader(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ForceSignatureAnnotationProcessor fsAnnotationProcessor = new ForceSignatureAnnotationProcessor();
        submissionClassReader.accept(fsAnnotationProcessor, 0);
        SubmissionClassInfo submissionClassInfo = new SubmissionClassInfo(this, fsAnnotationProcessor);
        submissionClassReader.accept(submissionClassInfo, 0);
        return submissionClassInfo;
    }

    // Solution classes

    /**
     * Returns the solution class name for the given submission class name.
     * If no matching solution class was found, returns the given submission class name.
     *
     * @param submissionClassName the submission class name
     * @return the solution class name
     */
    public String getSolutionClassName(String submissionClassName) {
        return classSimilarityMapper.getBestMatch(submissionClassName).orElse(submissionClassName);
    }

    /**
     * Returns the solution class node for the given solution class name.
     *
     * @param name the solution class name
     * @return the solution class node
     */
    public SolutionClassNode getSolutionClass(String name) {
        return solutionClasses.get(name);
    }

    public Map<String, SolutionClassNode> getSolutionClasses() {
        return Collections.unmodifiableMap(solutionClasses);
    }

    /**
     * Returns the computed (i.e., mapped from submission to solution) type.
     * The given value may be an internal class name or a descriptor.
     * If the value is a method descriptor, this method will return a descriptor where
     * all parameter types and the return types have been computed.
     * If the value is an array descriptor, it will return a descriptor where
     * the component type has been computed.
     * If the given internal class name or descriptor are not part of the submission
     * or no corresponding solution class exists, it will return a {@link Type} object
     * representing the original name / descriptor.
     *
     * @param descriptor the class name or descriptor
     * @return the computed type
     */
    public Type toComputedType(String descriptor) {
        if (descriptor.startsWith("(")) {  // method descriptor
            return toComputedType(Type.getMethodType(descriptor));
        } else if (descriptor.startsWith("[") || descriptor.endsWith(";")) {  // array or reference descriptor
            return toComputedType(Type.getType(descriptor));
        } else if (descriptor.length() == 1 && "VZBSCIFJD".contains(descriptor)) {  // primitive type
            return Type.getType(descriptor);
        } else {
            return toComputedType(Type.getObjectType(descriptor));
        }
    }

    /**
     * Returns the computed (i.e., mapped from submission to solution) type.
     * If the given value represents a method descriptor, this method will return a type with
     * a descriptor where all parameter types and the return types have been computed.
     * If the value represents an array, it will return a type where the component type has been computed.
     * If the given type represents a primitive type or an object type that is not a submission class
     * (or no corresponding solution class exists), it will return the original value.
     *
     * @param type the type to map
     * @return the computed type
     */
    public Type toComputedType(Type type) {
        if (type.getSort() == Type.OBJECT) {
            return Type.getObjectType(getSolutionClassName(type.getInternalName()));
        } else if (type.getSort() == Type.ARRAY) {
            int dimensions = type.getDimensions();
            Type elementType = type.getElementType();
            return Type.getType("[".repeat(dimensions) + toComputedType(elementType).getDescriptor());
        } else if (type.getSort() == Type.METHOD) {
            Type returnType = toComputedType(type.getReturnType());
            Type[] parameterTypes = Arrays.stream(type.getArgumentTypes()).map(this::toComputedType).toArray(Type[]::new);
            return Type.getMethodType(returnType, parameterTypes);
        } else {
            return type;
        }
    }

    /**
     * Attempts to read and process a solution class from {@code resources/classes/}.
     *
     * @param className the name of the solution class
     * @return the resulting {@link SolutionClassNode} object
     */
    public SolutionClassNode readSolutionClass(String className) {
        ClassReader solutionClassReader;
        String solutionClassFilePath = "/classes/%s.bin".formatted(className);
        try (InputStream is = getClass().getResourceAsStream(solutionClassFilePath)) {
            if (is == null) {
                throw new IOException("No such resource: " + solutionClassFilePath);
            }
            solutionClassReader = new ClassReader(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        SolutionClassNode solutionClassNode = new SolutionClassNode(this, className);
        solutionClassReader.accept(solutionClassNode, 0);
        return solutionClassNode;
    }

    @Override
    public String toString() {
        return "TransformationContext[configuration=%s, solutionClasses=%s, submissionClasses=%s]"
            .formatted(configuration, solutionClasses, submissionClasses);
    }
}
