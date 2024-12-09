package org.tudalgo.algoutils.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.objectweb.asm.Opcodes;
import org.sourcegrade.jagr.api.testing.extension.JagrExecutionCondition;
import org.tudalgo.algoutils.student.annotation.ForceSignature;
import org.tudalgo.algoutils.transform.classes.*;
import org.tudalgo.algoutils.transform.util.headers.MethodHeader;
import org.tudalgo.algoutils.transform.util.TransformationContext;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.sourcegrade.jagr.api.testing.ClassTransformer;
import org.tudalgo.algoutils.tutor.general.SubmissionInfo;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A class transformer that allows logging, substitution and delegation of method invocations.
 * This transformer uses two source sets: solution classes and submission classes.
 * <br><br>
 * <b>Solution classes</b>
 * <p>
 *     Solution classes are compiled java classes located in the {@code resources/classes/} directory.
 *     Their class structure must match the one defined in the exercise sheet but they may define additional
 *     members such as fields and methods.
 *     Additional types (classes, interfaces, enums, etc.) may also be defined.
 *     <br>
 *     The directory structure must match the one in the output / build directory.
 *     Due to limitations of {@link Class#getResourceAsStream(String)} the compiled classes cannot have
 *     the {@code .class} file extension, so {@code .bin} is used instead.
 *     For example, a class {@code MyClass} with an inner class {@code Inner} in package {@code my.package}
 *     would be compiled to {@code my/package/MyClass.class} and {@code my/package/MyClass$Inner.class}.
 *     In the solution classes directory they would be located at {@code resources/classes/my/package/MyClass.bin}
 *     and {@code resources/classes/my/package/MyClass$Inner.bin}, respectively.
 * </p>
 *
 * <b>Submission classes</b>
 * <p>
 *     Submission classes are the original java classes in the main module.
 *     They are compiled externally and processed one by one using {@link #transform(ClassReader, ClassWriter)}.
 *     In case classes or members are misnamed, this transformer will attempt to map them to the closest
 *     matching solution class / member.
 *     If both the direct and similarity matching approach fail, the intended target can be explicitly
 *     specified using the {@link ForceSignature} annotation.
 * </p>
 * <br><br>
 * Implementation details:
 * <ul>
 *     <li>
 *         Unless otherwise specified, this transformer and its tools use the internal name as specified by
 *         {@link Type#getInternalName()} when referring to class names.
 *     </li>
 *     <li>
 *         The term "descriptor" refers to the bytecode-level representation of types, such as
 *         field types, method return types or method parameter types as specified by
 *         <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3">Chapter 4.3</a>
 *         of the Java Virtual Machine Specification.
 *     </li>
 * </ul>
 *
 * @see SubmissionClassVisitor
 * @see SubmissionExecutionHandler
 * @author Daniel Mangold
 */
@SuppressWarnings("unused")
public class SolutionMergingClassTransformer implements ClassTransformer {

    /**
     * An object providing context throughout the transformation processing chain.
     */
    private final TransformationContext transformationContext;
    private boolean readSubmissionClasses = false;

    /**
     * Constructs a new {@link SolutionMergingClassTransformer} instance.
     *
     * @param projectPrefix            the root package containing all submission classes, usually the sheet number
     * @param availableSolutionClasses the list of solution class names (fully qualified) to use
     */
    public SolutionMergingClassTransformer(String projectPrefix, String... availableSolutionClasses) {
        this(Arrays.stream(availableSolutionClasses).reduce(new Builder(projectPrefix),
            Builder::addSolutionClass,
            (builder, builder2) -> builder));
    }

    /**
     * Constructs a new {@link SolutionMergingClassTransformer} instance with config settings from
     * the given builder.
     *
     * @param builder the builder object
     */
    @SuppressWarnings("unchecked")
    private SolutionMergingClassTransformer(Builder builder) {
        Map<String, SolutionClassNode> solutionClasses = new HashMap<>();
        Map<String, SubmissionClassInfo> submissionClasses = new ConcurrentHashMap<>();
        this.transformationContext = new TransformationContext(Collections.unmodifiableMap(builder.configuration),
            solutionClasses,
            submissionClasses);
        ((Map<String, List<String>>) builder.configuration.get(Config.SOLUTION_CLASSES)).keySet()
            .forEach(className -> solutionClasses.put(className, transformationContext.readSolutionClass(className)));
    }

    @Override
    public String getName() {
        return SolutionMergingClassTransformer.class.getSimpleName();
    }

    @Override
    public int getWriterFlags() {
        return ClassWriter.COMPUTE_MAXS;
    }

    @Override
    public void transform(ClassReader reader, ClassWriter writer) {
        if (!new JagrExecutionCondition().evaluateExecutionCondition(null).isDisabled()) {  // if Jagr is present
            try {
                Method getClassLoader = ClassWriter.class.getDeclaredMethod("getClassLoader");
                getClassLoader.setAccessible(true);
                ClassLoader submissionClassLoader = (ClassLoader) getClassLoader.invoke(writer);
                transformationContext.setSubmissionClassLoader(submissionClassLoader);

                if (!readSubmissionClasses) {
                    Set<String> submissionClassNames = new ObjectMapper()
                        .readValue(submissionClassLoader.getResourceAsStream("submission-info.json"), SubmissionInfo.class)
                        .sourceSets()
                        .stream()
                        .flatMap(sourceSet -> {
                            List<String> classNames = sourceSet.files().get("java");
                            return classNames != null ? classNames.stream() : null;
                        })
                        .map(submissionClassName -> submissionClassName.replaceAll("\\.java$", ""))
                        .collect(Collectors.toSet());
                    transformationContext.setSubmissionClassNames(submissionClassNames);
                    transformationContext.computeClassesSimilarity();

                    readSubmissionClasses = true;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            // TODO: fix this for regular JUnit run
            transformationContext.setSubmissionClassLoader(null);
        }

        if ((reader.getAccess() & Opcodes.ACC_ENUM) != 0) {
            reader.accept(new SubmissionEnumClassVisitor(writer, transformationContext, reader.getClassName()), 0);
        } else {
            reader.accept(new SubmissionClassVisitor(writer, transformationContext, reader.getClassName()), 0);
        }
    }

    @Override
    public Map<String, byte[]> injectClasses() {
        Set<String> visitedClasses = transformationContext.getVisitedClasses();
        Map<String, byte[]> missingClasses = new HashMap<>();

        transformationContext.getSolutionClasses()
            .entrySet()
            .stream()
            .filter(entry -> !visitedClasses.contains(entry.getKey()))
            .forEach(entry -> {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                entry.getValue().accept(new MissingClassVisitor(writer, transformationContext, entry.getValue()));
                missingClasses.put(entry.getKey().replace('/', '.'), writer.toByteArray());
            });

        return missingClasses;
    }

    /**
     * (Internal) Configuration keys
     */
    public enum Config {
        PROJECT_PREFIX(null),
        SOLUTION_CLASSES(new HashMap<>()),
        SIMILARITY(0.90),
        METHOD_REPLACEMENTS(new HashMap<MethodHeader, MethodHeader>());

        private final Object defaultValue;

        Config(Object defaultValue) {
            this.defaultValue = defaultValue;
        }
    }

    /**
     * Builder for {@link SolutionMergingClassTransformer}.
     */
    public static class Builder {

        private final Map<Config, Object> configuration = new EnumMap<>(Config.class);

        /**
         * Constructs a new {@link Builder}.
         *
         * @param projectPrefix   the root package containing all submission classes, usually the sheet number
         */
        public Builder(String projectPrefix) {
            for (Config config : Config.values()) {
                configuration.put(config, config.defaultValue);
            }
            configuration.put(Config.PROJECT_PREFIX, projectPrefix);
        }

        @SuppressWarnings("unchecked")
        public Builder addSolutionClass(String solutionClassName, String... altNames) {
            ((Map<String, Collection<String>>) configuration.get(Config.SOLUTION_CLASSES)).put(
                solutionClassName.replace('.', '/'),
                Arrays.stream(altNames).map(s -> s.replace('.', '/')).toList()
            );
            return this;
        }

        /**
         * Sets the threshold for matching submission classes to solution classes via similarity matching.
         *
         * @param similarity the new similarity threshold
         * @return the builder object
         */
        public Builder setSimilarity(double similarity) {
            configuration.put(Config.SIMILARITY, similarity);
            return this;
        }

        /**
         * Replaces all calls to the target executable with calls to the replacement executable.
         * The replacement executable must be accessible from the calling class, be static and declare
         * the same parameter types and return type as the target.
         * If the target executable is not static, the replacement must declare an additional parameter
         * at the beginning to receive the object the target was called on.<br>
         * Example:<br>
         * Target: {@code public boolean equals(Object)} in class {@code String} =>
         * Replacement: {@code public static boolean <name>(String, Object)}
         *
         * @param targetExecutable      the targeted method / constructor
         * @param replacementExecutable the replacement method / constructor
         * @return the builder object
         */
        public Builder addMethodReplacement(Executable targetExecutable, Executable replacementExecutable) {
            return addMethodReplacement(new MethodHeader(targetExecutable), new MethodHeader(replacementExecutable));
        }

        /**
         * Replaces all calls to the matching the target's method header with calls to the replacement.
         * The replacement must be accessible from the calling class, be static and declare
         * the same parameter types and return type as the target.
         * If the target is not static, the replacement must declare an additional parameter
         * at the beginning to receive the object the target was called on.<br>
         * Example:<br>
         * Target: {@code public boolean equals(Object)} in class {@code String} =>
         * Replacement: {@code public static boolean <name>(String, Object)}
         *
         * @param targetMethodHeader      the header of the targeted method / constructor
         * @param replacementMethodHeader the header of the replacement method / constructor
         * @return the builder object
         */
        @SuppressWarnings("unchecked")
        public Builder addMethodReplacement(MethodHeader targetMethodHeader, MethodHeader replacementMethodHeader) {
            if (!Modifier.isStatic(replacementMethodHeader.access())) {
                throw new IllegalArgumentException("Replacement method " + replacementMethodHeader + " is not static");
            }

            ((Map<MethodHeader, MethodHeader>) configuration.get(Config.METHOD_REPLACEMENTS))
                .put(targetMethodHeader, replacementMethodHeader);
            return this;
        }

        /**
         * Constructs the transformer.
         *
         * @return the configured {@link SolutionMergingClassTransformer} object
         */
        public SolutionMergingClassTransformer build() {
            return new SolutionMergingClassTransformer(this);
        }
    }
}
