package org.tudalgo.algoutils.transform;

import org.tudalgo.algoutils.transform.util.*;
import kotlin.Pair;
import kotlin.Triple;
import org.objectweb.asm.*;
import org.tudalgo.algoutils.tutor.general.match.MatchingUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

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
    private final String originalClassName;
    private final String computedClassName;
    private final Set<Triple<String, Map<FieldHeader, FieldHeader>, Map<MethodHeader, MethodHeader>>> superClassMembers = new HashSet<>();
    private final ForceSignatureAnnotationProcessor fsAnnotationProcessor;
    private final SolutionClassNode solutionClass;

    private String superClass;
    private String[] interfaces;

    private ClassHeader submissionClassHeader;

    // Mapping of fields in submission => usable fields
    private final Map<FieldHeader, FieldHeader> fields = new HashMap<>();

    // Mapping of methods in submission => usable methods
    private final Map<MethodHeader, MethodHeader> methods = new HashMap<>();

    /**
     * Constructs a new {@link SubmissionClassInfo} instance.
     *
     * @param transformationContext a {@link TransformationContext} object
     * @param className             the name of the submission class
     * @param fsAnnotationProcessor a {@link ForceSignatureAnnotationProcessor} for the submission class
     */
    public SubmissionClassInfo(TransformationContext transformationContext,
                               String className,
                               ForceSignatureAnnotationProcessor fsAnnotationProcessor) {
        super(Opcodes.ASM9);
        this.transformationContext = transformationContext;
        this.originalClassName = className;
        this.fsAnnotationProcessor = fsAnnotationProcessor;

        if (fsAnnotationProcessor.classIdentifierIsForced()) {
            this.computedClassName = fsAnnotationProcessor.forcedClassIdentifier();
        } else {
            // If not forced, get the closest matching solution class (at least 90% similarity)
            this.computedClassName = transformationContext.solutionClasses()
                .keySet()
                .stream()
                .map(s -> new Pair<>(s, MatchingUtils.similarity(originalClassName, s)))
                .filter(pair -> pair.getSecond() >= 0.90)
                .max(Comparator.comparing(Pair::getSecond))
                .map(Pair::getFirst)
                .orElse(originalClassName);
        }
        this.solutionClass = transformationContext.solutionClasses().get(computedClassName);
    }

    /**
     * Returns the original class header.
     *
     * @return the original class header
     */
    public ClassHeader getOriginalClassHeader() {
        return submissionClassHeader;
    }

    /**
     * Returns the computed class name.
     * The computed name is the name of the associated solution class, if one is present.
     * If no solution class is present, the computed names equals the original submission class name.
     *
     * @return the computed class name
     */
    public String getComputedClassName() {
        return computedClassName;
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

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        submissionClassHeader = new ClassHeader(access, name, signature, superName, interfaces);
        resolveSuperClassMembers(superClassMembers, this.superClass = superName, this.interfaces = interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        FieldHeader submissionFieldHeader = new FieldHeader(originalClassName, access, name, descriptor, signature);
        FieldHeader solutionFieldHeader;
        if (fsAnnotationProcessor.fieldIdentifierIsForced(name)) {
            solutionFieldHeader = fsAnnotationProcessor.forcedFieldHeader(name);
        } else if (solutionClass != null) {
            solutionFieldHeader = solutionClass.getFields()
                .keySet()
                .stream()
                .map(fieldHeader -> new Pair<>(fieldHeader, MatchingUtils.similarity(name, fieldHeader.name())))
                .filter(pair -> pair.getSecond() >= 0.90)
                .max(Comparator.comparing(Pair::getSecond))
                .map(Pair::getFirst)
                .orElse(submissionFieldHeader);
        } else {
            solutionFieldHeader = submissionFieldHeader;
        }

        fields.put(submissionFieldHeader, solutionFieldHeader);
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodHeader submissionMethodHeader = new MethodHeader(originalClassName, access, name, descriptor, signature, exceptions);
        if ((access & ACC_SYNTHETIC) != 0 && name.startsWith("lambda$")) {
            methods.put(submissionMethodHeader, submissionMethodHeader);
            return null;
        }

        MethodHeader solutionMethodHeader;
        if (fsAnnotationProcessor.methodSignatureIsForced(name, descriptor)) {
            solutionMethodHeader = fsAnnotationProcessor.forcedMethodHeader(name, descriptor);
        } else if (solutionClass != null) {
            solutionMethodHeader = solutionClass.getMethods()
                .keySet()
                .stream()
                .map(methodHeader -> new Triple<>(methodHeader,
                    MatchingUtils.similarity(name, methodHeader.name()),
                    MatchingUtils.similarity(descriptor, methodHeader.descriptor())))
                .filter(triple -> triple.getSecond() >= 0.90 && triple.getThird() >= 0.90)
                .max(Comparator.comparing(Triple<MethodHeader, Double, Double>::getSecond).thenComparing(Triple::getThird))
                .map(Triple::getFirst)
                .orElse(submissionMethodHeader);
        } else {
            solutionMethodHeader = submissionMethodHeader;
        }

        methods.put(submissionMethodHeader, solutionMethodHeader);
        return null;
    }

    @Override
    public void visitEnd() {
        for (Triple<String, Map<FieldHeader, FieldHeader>, Map<MethodHeader, MethodHeader>> triple : superClassMembers) {
            triple.getSecond().forEach(fields::putIfAbsent);
            triple.getThird().forEach(methods::putIfAbsent);
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
        if (className.startsWith(transformationContext.projectPrefix())) {
            SubmissionClassInfo submissionClassInfo = transformationContext.getSubmissionClassInfo(className);
            superClassMembers.add(new Triple<>(className, submissionClassInfo.fields, submissionClassInfo.methods));
            resolveSuperClassMembers(superClassMembers, submissionClassInfo.superClass, submissionClassInfo.interfaces);
        } else {
            try {
                Class<?> clazz = Class.forName(className.replace('/', '.'));
                Map<FieldHeader, FieldHeader> fieldHeaders = new HashMap<>();
                for (Field field : clazz.getDeclaredFields()) {
                    if ((field.getModifiers() & Modifier.PRIVATE) != 0) continue;
                    FieldHeader fieldHeader = new FieldHeader(
                        className,
                        field.getModifiers(),
                        field.getName(),
                        Type.getDescriptor(field.getType()),
                        null
                    );
                    fieldHeaders.put(fieldHeader, fieldHeader);
                }
                Map<MethodHeader, MethodHeader> methodHeaders = new HashMap<>();
                for (Method method : clazz.getDeclaredMethods()) {
                    if ((method.getModifiers() & Modifier.PRIVATE) != 0) continue;
                    MethodHeader methodHeader = new MethodHeader(
                        className,
                        method.getModifiers(),
                        method.getName(),
                        Type.getMethodDescriptor(method),
                        null,
                        Arrays.stream(method.getExceptionTypes()).map(Type::getInternalName).toArray(String[]::new)
                    );
                    methodHeaders.put(methodHeader, methodHeader);
                }
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
