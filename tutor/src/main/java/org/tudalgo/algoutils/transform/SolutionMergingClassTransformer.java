package org.tudalgo.algoutils.transform;

import org.tudalgo.algoutils.student.annotation.ForceSignature;
import org.tudalgo.algoutils.transform.util.TransformationContext;
import org.tudalgo.algoutils.transform.util.TransformationUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.sourcegrade.jagr.api.testing.ClassTransformer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
public class SolutionMergingClassTransformer implements ClassTransformer {

    /**
     * An object providing context throughout the transformation processing chain.
     */
    private final TransformationContext transformationContext;

    /**
     * Constructs a new {@link SolutionMergingClassTransformer} instance.
     *
     * @param projectPrefix            the root package containing all submission classes, usually the sheet number
     * @param availableSolutionClasses the list of solution class names (fully qualified) to use
     */
    public SolutionMergingClassTransformer(String projectPrefix, List<String> availableSolutionClasses) {
        Map<String, SolutionClassNode> solutionClasses = new HashMap<>();
        Map<String, SubmissionClassInfo> submissionClasses = new ConcurrentHashMap<>();
        this.transformationContext = new TransformationContext(projectPrefix, solutionClasses, submissionClasses);
        availableSolutionClasses.stream()
            .map(s -> s.replace('.', '/'))
            .forEach(className -> solutionClasses.put(className, TransformationUtils.readSolutionClass(className)));
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
        String submissionClassName = reader.getClassName();
        reader.accept(new SubmissionClassVisitor(writer, transformationContext, submissionClassName), 0);
    }
}
