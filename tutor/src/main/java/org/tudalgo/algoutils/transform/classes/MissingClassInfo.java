package org.tudalgo.algoutils.transform.classes;

import org.objectweb.asm.ClassVisitor;
import org.tudalgo.algoutils.transform.util.headers.ClassHeader;
import org.tudalgo.algoutils.transform.util.headers.FieldHeader;
import org.tudalgo.algoutils.transform.util.headers.MethodHeader;
import org.tudalgo.algoutils.transform.util.TransformationContext;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Holds information about a class that is absent from the submission but present in the solution.
 * <p>
 *     Original and computed header methods return the same values since this class can only be
 *     used with solution classes.
 *     For the same reason, using {@link ClassVisitor} methods has no effect.
 * </p>
 *
 * @author Daniel Mangold
 */
public class MissingClassInfo extends ClassInfo {

    private final SolutionClassNode solutionClassNode;

    /**
     * Constructs a new {@link MissingClassInfo} instance using the information stored
     * in the given solution class.
     *
     * @param transformationContext the transformation context
     * @param solutionClassNode     the solution class
     */
    public MissingClassInfo(TransformationContext transformationContext, SolutionClassNode solutionClassNode) {
        super(transformationContext);

        this.solutionClassNode = solutionClassNode;
        solutionClassNode.getFields()
            .keySet()
            .forEach(fieldHeader -> fields.put(fieldHeader, fieldHeader));
        solutionClassNode.getMethods()
            .keySet()
            .forEach(methodHeader -> methods.put(methodHeader, methodHeader));
        resolveSuperTypeMembers(superTypeMembers, getOriginalClassHeader().superName());
    }

    @Override
    public ClassHeader getOriginalClassHeader() {
        return solutionClassNode.getClassHeader();
    }

    @Override
    public ClassHeader getComputedClassHeader() {
        return getOriginalClassHeader();
    }

    @Override
    public Set<FieldHeader> getOriginalFieldHeaders() {
        return fields.keySet();
    }

    @Override
    public FieldHeader getComputedFieldHeader(String name) {
        return fields.keySet()
            .stream()
            .filter(fieldHeader -> fieldHeader.name().equals(name))
            .findAny()
            .orElseThrow();
    }

    @Override
    public Set<MethodHeader> getOriginalMethodHeaders() {
        return methods.keySet();
    }

    @Override
    public MethodHeader getComputedMethodHeader(String name, String descriptor) {
        return methods.keySet()
            .stream()
            .filter(methodHeader -> methodHeader.name().equals(name) && methodHeader.descriptor().equals(descriptor))
            .findAny()
            .orElseThrow();
    }

    @Override
    public Set<MethodHeader> getOriginalSuperClassConstructorHeaders() {
        return superClassConstructors.keySet();
    }

    @Override
    public MethodHeader getComputedSuperClassConstructorHeader(String descriptor) {
        return superClassConstructors.keySet()
            .stream()
            .filter(methodHeader -> methodHeader.descriptor().equals(descriptor))
            .findAny()
            .orElseThrow();
    }

    @Override
    protected void resolveSuperTypeMembers(Set<SuperTypeMembers> superTypeMembers, String typeName) {
        if (typeName == null) {
            resolveSuperTypeMembers(superTypeMembers, "java/lang/Object");
            return;
        }

        Optional<SolutionClassNode> superClassNode = Optional.ofNullable(transformationContext.getSolutionClass(typeName));
        if (superClassNode.isPresent()) {
            superTypeMembers.add(new SuperTypeMembers(
                typeName,
                Collections.emptyMap(),
                superClassNode.get()
                    .getMethods()
                    .keySet()
                    .stream()
                    .collect(Collectors.toMap(Function.identity(), Function.identity()))
            ));
        } else {
            resolveExternalSuperTypeMembers(superTypeMembers, typeName, false);
        }
    }
}
