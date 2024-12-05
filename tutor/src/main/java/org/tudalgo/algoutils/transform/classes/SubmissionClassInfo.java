package org.tudalgo.algoutils.transform.classes;

import org.tudalgo.algoutils.transform.util.*;
import org.objectweb.asm.*;
import org.tudalgo.algoutils.transform.util.matching.FieldSimilarityMapper;
import org.tudalgo.algoutils.transform.util.matching.MethodSimilarityMapper;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A class that holds information on a submission class.
 * <p>
 *     This class will attempt to find a corresponding solution class and map its members
 *     to the ones defined in the solution class.
 *     If no solution class can be found, for example because the submission class was added
 *     as a utility class, it will map its members to themselves to remain usable.
 * </p>
 * <p>
 *     Note: Since this class resolves members of supertypes recursively, it may lead to a stack overflow
 *     if it does so all in one step.
 *     Therefore, members (both of this class and supertypes) are only fully resolved once
 *     {@link #resolveMembers()} is called.
 * </p>
 *
 * @author Daniel Mangold
 */
public class SubmissionClassInfo extends ClassInfo {

    private final ForceSignatureAnnotationProcessor fsAnnotationProcessor;

    private ClassHeader originalClassHeader;
    private ClassHeader computedClassHeader;
    private SolutionClassNode solutionClass;

    /**
     * Constructs a new {@link SubmissionClassInfo} instance.
     *
     * @param transformationContext a {@link TransformationContext} object
     * @param fsAnnotationProcessor a {@link ForceSignatureAnnotationProcessor} for the submission class
     */
    public SubmissionClassInfo(TransformationContext transformationContext,
                               ForceSignatureAnnotationProcessor fsAnnotationProcessor) {
        super(transformationContext);

        this.fsAnnotationProcessor = fsAnnotationProcessor;
    }

    @Override
    public ClassHeader getOriginalClassHeader() {
        return originalClassHeader;
    }

    @Override
    public ClassHeader getComputedClassHeader() {
        return computedClassHeader;
    }

    @Override
    public Set<FieldHeader> getOriginalFieldHeaders() {
        return fields.keySet();
    }

    @Override
    public FieldHeader getComputedFieldHeader(String name) {
        return fields.entrySet()
            .stream()
            .filter(entry -> entry.getKey().name().equals(name))
            .findAny()
            .map(Map.Entry::getValue)
            .orElseThrow();
    }

    @Override
    public Set<MethodHeader> getOriginalMethodHeaders() {
        return methods.keySet();
    }

    @Override
    public MethodHeader getComputedMethodHeader(String name, String descriptor) {
        return methods.entrySet()
            .stream()
            .filter(entry -> entry.getKey().name().equals(name) && entry.getKey().descriptor().equals(descriptor))
            .findAny()
            .map(Map.Entry::getValue)
            .orElseThrow();
    }

    @Override
    public Set<MethodHeader> getOriginalSuperClassConstructorHeaders() {
        return superClassConstructors.keySet();
    }

    @Override
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
        // TODO: make sure interfaces is not null
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

    /**
     * Returns the solution class associated with this submission class.
     *
     * @return an {@link Optional} object wrapping the associated solution class
     */
    public Optional<SolutionClassNode> getSolutionClass() {
        return Optional.ofNullable(solutionClass);
    }

    /**
     * Resolves the members of this submission class and all members from supertypes it inherits.
     * This method <i>must</i> be called once to use this {@link SubmissionClassInfo} instance.
     */
    public void resolveMembers() {
        FieldSimilarityMapper fieldsSimilarityMapper = new FieldSimilarityMapper(
            fields.keySet(),
            getSolutionClass().map(solutionClass -> solutionClass.getFields().keySet()).orElseGet(Collections::emptySet),
            transformationContext
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

        MethodSimilarityMapper methodsSimilarityMapper = new MethodSimilarityMapper(
            methods.keySet(),
            getSolutionClass().map(solutionClass -> solutionClass.getMethods().keySet()).orElseGet(Collections::emptySet),
            transformationContext
        );
        for (MethodHeader submissionMethodHeader : methods.keySet()) {
            String submissionMethodName = submissionMethodHeader.name();
            String submissionMethodDescriptor = submissionMethodHeader.descriptor();
            Supplier<MethodHeader> fallbackMethodHeader = () -> new MethodHeader(computedClassHeader.name(),
                submissionMethodHeader.access(),
                submissionMethodHeader.name(),
                transformationContext.toComputedDescriptor(submissionMethodHeader.descriptor()),
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

        resolveSuperTypeMembers(superTypeMembers, originalClassHeader.superName(), originalClassHeader.interfaces());
        for (SuperTypeMembers superTypeMembers : superTypeMembers) {
            if (superTypeMembers.typeName().equals(originalClassHeader.superName())) {
                superTypeMembers.methods()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().name().equals("<init>"))
                    .forEach(entry -> superClassConstructors.put(entry.getKey(), entry.getValue()));
            }
            superTypeMembers.fields().forEach(fields::putIfAbsent);
            superTypeMembers.methods()
                .entrySet()
                .stream()
                .filter(entry -> !entry.getKey().name().equals("<init>"))
                .forEach(entry -> methods.putIfAbsent(entry.getKey(), entry.getValue()));
        }
    }

    @Override
    protected void resolveSuperTypeMembers(Set<SuperTypeMembers> superTypeMembers, String typeName) {
        if (typeName == null) return;

        if (transformationContext.isSubmissionClass(typeName)) {
            SubmissionClassInfo submissionClassInfo = transformationContext.getSubmissionClassInfo(typeName);
            superTypeMembers.add(new SuperTypeMembers(typeName,
                submissionClassInfo.fields.entrySet()
                    .stream()
                    .filter(entry -> !Modifier.isPrivate(entry.getKey().access()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                submissionClassInfo.methods.entrySet()
                    .stream()
                    .filter(entry -> !Modifier.isPrivate(entry.getKey().access()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
            resolveSuperTypeMembers(superTypeMembers,
                submissionClassInfo.originalClassHeader.superName(),
                submissionClassInfo.originalClassHeader.interfaces());
        } else {
            resolveExternalSuperTypeMembers(superTypeMembers, typeName, true);
        }
    }
}
