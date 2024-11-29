package org.tudalgo.algoutils.transform;

import org.tudalgo.algoutils.transform.util.*;
import kotlin.Triple;
import org.objectweb.asm.*;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class that holds information on a submission class.
 * This class will attempt to find a corresponding solution class and map its members
 * to the ones defined in the solution class.
 * If no solution class can be found, for example because the submission class was added
 * as a utility class, it will map its members to itself to remain usable.
 *
 * @author Daniel Mangold
 */
public class SubmissionClassInfo extends ClassVisitor {

    private final TransformationContext transformationContext;
    private final ForceSignatureAnnotationProcessor fsAnnotationProcessor;
    private final Set<Triple<String, Map<FieldHeader, FieldHeader>, Map<MethodHeader, MethodHeader>>> superClassMembers = new HashSet<>();

    private ClassHeader originalClassHeader;
    private ClassHeader computedClassHeader;
    private SolutionClassNode solutionClass;

    // Mapping of fields in submission => usable fields
    private final Map<FieldHeader, FieldHeader> fields = new HashMap<>();

    // Mapping of methods in submission => usable methods
    private final Map<MethodHeader, MethodHeader> methods = new HashMap<>();

    private final Map<MethodHeader, MethodHeader> superClassConstructors = new HashMap<>();

    /**
     * Constructs a new {@link SubmissionClassInfo} instance.
     *
     * @param transformationContext a {@link TransformationContext} object
     * @param fsAnnotationProcessor a {@link ForceSignatureAnnotationProcessor} for the submission class
     */
    public SubmissionClassInfo(TransformationContext transformationContext,
                               ForceSignatureAnnotationProcessor fsAnnotationProcessor) {
        super(Opcodes.ASM9);
        this.transformationContext = transformationContext;
        this.fsAnnotationProcessor = fsAnnotationProcessor;
    }

    /**
     * Returns the original class header.
     *
     * @return the original class header
     */
    public ClassHeader getOriginalClassHeader() {
        return originalClassHeader;
    }

    /**
     * Returns the computed class name.
     * The computed name is the name of the associated solution class, if one is present.
     * If no solution class is present, the computed names equals the original submission class name.
     *
     * @return the computed class name
     */
    public ClassHeader getComputedClassHeader() {
        return computedClassHeader;
    }

    /**
     * Returns the solution class associated with this submission class.
     *
     * @return an {@link Optional} object wrapping the associated solution class
     */
    public Optional<SolutionClassNode> getSolutionClass() {
        return Optional.ofNullable(solutionClass);
    }

    /**
     * Returns the original field headers for this class.
     *
     * @return the original field headers
     */
    public Set<FieldHeader> getOriginalFieldHeaders() {
        return fields.keySet();
    }

    /**
     * Returns the computed field header for the given field name.
     * The computed field header is the field header of the corresponding field in the solution class,
     * if one is present.
     * If no solution class is present, the computed field header equals the original field header
     * in the submission class.
     *
     * @param name the field name
     * @return the computed field header
     */
    public FieldHeader getComputedFieldHeader(String name) {
        return fields.entrySet()
            .stream()
            .filter(entry -> entry.getKey().name().equals(name))
            .findAny()
            .map(Map.Entry::getValue)
            .orElseThrow();
    }

    /**
     * Return the original method headers for this class.
     *
     * @return the original method headers
     */
    public Set<MethodHeader> getOriginalMethodHeaders() {
        return methods.keySet();
    }

    /**
     * Returns the computed method header for the given method signature.
     * The computed method header is the method header of the corresponding method in the solution class,
     * if one is present.
     * If no solution class is present, the computed method header equals the original method header
     * in the submission class.
     *
     * @param name       the method name
     * @param descriptor the method descriptor
     * @return the computed method header
     */
    public MethodHeader getComputedMethodHeader(String name, String descriptor) {
        return methods.entrySet()
            .stream()
            .filter(entry -> entry.getKey().name().equals(name) && entry.getKey().descriptor().equals(descriptor))
            .findAny()
            .map(Map.Entry::getValue)
            .orElseThrow();
    }

    public Set<MethodHeader> getOriginalSuperClassConstructorHeaders() {
        return superClassConstructors.keySet();
    }

    public MethodHeader getComputedSuperClassConstructorHeader(String descriptor) {
        return superClassConstructors.entrySet()
            .stream()
            .filter(entry -> entry.getKey().descriptor().equals(descriptor))
            .findAny()
            .map(Map.Entry::getValue)
            .orElseThrow();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        originalClassHeader = new ClassHeader(access, name, signature, superName, interfaces);
        String computedClassName;
        if (fsAnnotationProcessor.classIdentifierIsForced()) {
            computedClassName = fsAnnotationProcessor.forcedClassIdentifier();
        } else {
            // If not forced, get the closest matching solution class
            computedClassName = transformationContext.getSolutionClassName(originalClassHeader.name());
        }
        solutionClass = transformationContext.getSolutionClass(computedClassName);
        computedClassHeader = getSolutionClass().map(SolutionClassNode::getClassHeader).orElse(originalClassHeader);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        fields.put(new FieldHeader(originalClassHeader.name(), access, name, descriptor, signature), null);
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodHeader submissionMethodHeader = new MethodHeader(originalClassHeader.name(), access, name, descriptor, signature, exceptions);
        if (TransformationUtils.isLambdaMethod(access, name)) {
            methods.put(submissionMethodHeader, submissionMethodHeader);
            return null;
        }

        methods.put(submissionMethodHeader, null);
        return null;
    }

    public void computeMembers() {
        SimilarityMapper<FieldHeader> fieldsSimilarityMapper = new SimilarityMapper<>(
            fields.keySet(),
            getSolutionClass().map(solutionClass -> solutionClass.getFields().keySet()).orElseGet(Collections::emptySet),
            transformationContext.getSimilarity(),
            FieldHeader::name
        );
        for (FieldHeader submissionFieldHeader : fields.keySet()) {
            Supplier<FieldHeader> fallbackFieldHeader = () -> new FieldHeader(computedClassHeader.name(),
                submissionFieldHeader.access(),
                submissionFieldHeader.name(),
                transformationContext.toComputedType(Type.getType(submissionFieldHeader.descriptor())).getDescriptor(),
                submissionFieldHeader.signature());
            FieldHeader solutionFieldHeader;
            if (fsAnnotationProcessor.fieldIdentifierIsForced(submissionFieldHeader.name())) {
                solutionFieldHeader = fsAnnotationProcessor.forcedFieldHeader(submissionFieldHeader.name());
            } else if (solutionClass != null) {
                solutionFieldHeader = solutionClass.getFields()
                    .keySet()
                    .stream()
                    .filter(fieldHeader -> fieldsSimilarityMapper.getBestMatch(submissionFieldHeader)
                        .map(fieldHeader::equals)
                        .orElse(false))
                    .findAny()
                    .orElseGet(fallbackFieldHeader);
            } else {
                solutionFieldHeader = fallbackFieldHeader.get();
            }
            fields.put(submissionFieldHeader, solutionFieldHeader);
        }

        SimilarityMapper<MethodHeader> methodsSimilarityMapper = new SimilarityMapper<>(
            methods.keySet(),
            getSolutionClass().map(solutionClass -> solutionClass.getMethods().keySet()).orElseGet(Collections::emptySet),
            transformationContext.getSimilarity(),
            methodHeader -> methodHeader.name() + methodHeader.descriptor()
        );
        for (MethodHeader submissionMethodHeader : methods.keySet()) {
            String submissionMethodName = submissionMethodHeader.name();
            String submissionMethodDescriptor = submissionMethodHeader.descriptor();
            Supplier<MethodHeader> fallbackMethodHeader = () -> new MethodHeader(computedClassHeader.name(),
                submissionMethodHeader.access(),
                submissionMethodHeader.name(),
                transformationContext.toComputedType(submissionMethodHeader.descriptor()).getDescriptor(),
                submissionMethodHeader.signature(),
                submissionMethodHeader.exceptions());
            MethodHeader solutionMethodHeader;
            if (fsAnnotationProcessor.methodSignatureIsForced(submissionMethodName, submissionMethodDescriptor)) {
                solutionMethodHeader = fsAnnotationProcessor.forcedMethodHeader(submissionMethodName, submissionMethodDescriptor);
            } else if (solutionClass != null) {
                solutionMethodHeader = solutionClass.getMethods()
                    .keySet()
                    .stream()
                    .filter(methodHeader -> methodsSimilarityMapper.getBestMatch(submissionMethodHeader)
                        .map(methodHeader::equals)
                        .orElse(false))
                    .findAny()
                    .orElseGet(fallbackMethodHeader);
            } else {
                solutionMethodHeader = fallbackMethodHeader.get();
            }
            methods.put(submissionMethodHeader, solutionMethodHeader);
        }

        resolveSuperClassMembers(superClassMembers, originalClassHeader.superName(), originalClassHeader.interfaces());
        for (Triple<String, Map<FieldHeader, FieldHeader>, Map<MethodHeader, MethodHeader>> triple : superClassMembers) {
            if (triple.getFirst().equals(originalClassHeader.superName())) {
                triple.getThird()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().name().equals("<init>"))
                    .forEach(entry -> superClassConstructors.put(entry.getKey(), entry.getValue()));
            }
            triple.getSecond().forEach(fields::putIfAbsent);
            triple.getThird()
                .entrySet()
                .stream()
                .filter(entry -> !entry.getKey().name().equals("<init>"))
                .forEach(entry -> methods.putIfAbsent(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Recursively resolves the members of superclasses and interfaces.
     *
     * @param superClassMembers a set for recording class members
     * @param superClass        the name of the superclass to process
     * @param interfaces        the names of the interfaces to process
     */
    private void resolveSuperClassMembers(Set<Triple<String, Map<FieldHeader, FieldHeader>, Map<MethodHeader, MethodHeader>>> superClassMembers,
                                          String superClass,
                                          String[] interfaces) {
        resolveSuperClassMembers(superClassMembers, superClass);
        if (interfaces != null) {
            for (String interfaceName : interfaces) {
                resolveSuperClassMembers(superClassMembers, interfaceName);
            }
        }
    }

    /**
     * Recursively resolves the members of the given class.
     *
     * @param superClassMembers a set for recording class members
     * @param className         the name of the class / interface to process
     */
    private void resolveSuperClassMembers(Set<Triple<String, Map<FieldHeader, FieldHeader>, Map<MethodHeader, MethodHeader>>> superClassMembers,
                                          String className) {
        if (className == null) {
            return;
        }

        if (transformationContext.isSubmissionClass(className)) {
            SubmissionClassInfo submissionClassInfo = transformationContext.getSubmissionClassInfo(className);
            superClassMembers.add(new Triple<>(className,
                submissionClassInfo.fields.entrySet()
                    .stream()
                    .filter(entry -> !Modifier.isPrivate(entry.getKey().access()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                submissionClassInfo.methods.entrySet()
                    .stream()
                    .filter(entry -> !Modifier.isPrivate(entry.getKey().access()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
            resolveSuperClassMembers(superClassMembers,
                submissionClassInfo.originalClassHeader.superName(),
                submissionClassInfo.originalClassHeader.interfaces());
        } else {
            try {
                Class<?> clazz = Class.forName(className.replace('/', '.'));
                Map<FieldHeader, FieldHeader> fieldHeaders = Arrays.stream(clazz.getDeclaredFields())
                    .filter(field -> !Modifier.isPrivate(field.getModifiers()))
                    .map(FieldHeader::new)
                    .collect(Collectors.toMap(Function.identity(), Function.identity()));
                Map<MethodHeader, MethodHeader> methodHeaders = Stream.concat(
                        Arrays.stream(clazz.getDeclaredConstructors()),
                        Arrays.stream(clazz.getDeclaredMethods()))
                    .filter(executable -> !Modifier.isPrivate(executable.getModifiers()))
                    .map(MethodHeader::new)
                    .collect(Collectors.toMap(Function.identity(), Function.identity()));
                superClassMembers.add(new Triple<>(className, fieldHeaders, methodHeaders));
                if (clazz.getSuperclass() != null) {
                    resolveSuperClassMembers(superClassMembers,
                        Type.getInternalName(clazz.getSuperclass()),
                        Arrays.stream(clazz.getInterfaces()).map(Type::getInternalName).toArray(String[]::new));
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
