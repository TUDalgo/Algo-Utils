package org.tudalgo.algoutils.transform.classes;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.tudalgo.algoutils.transform.util.ClassHeader;
import org.tudalgo.algoutils.transform.util.FieldHeader;
import org.tudalgo.algoutils.transform.util.MethodHeader;
import org.tudalgo.algoutils.transform.util.TransformationContext;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class holds information about a class, such as its header, fields and methods.
 * <p>
 *     There are two sets of methods: those returning the original headers and those returning computed variants.
 *     The original header methods return the headers as they were declared in the class.
 *     The computed header methods may perform transforming operations on the header or map them to some other
 *     header and return the result of that operation.
 * </p>
 * <p>
 *     The class extends {@link ClassVisitor} so its methods can be used to read classes
 *     using the visitor pattern.
 * </p>
 *
 * @author Daniel Mangold
 */
public abstract class ClassInfo extends ClassVisitor {

    protected final TransformationContext transformationContext;
    protected final Set<SuperTypeMembers> superTypeMembers = new HashSet<>();

    protected final Map<FieldHeader, FieldHeader> fields = new HashMap<>(); // Mapping of fields in submission => usable fields
    protected final Map<MethodHeader, MethodHeader> methods = new HashMap<>(); // Mapping of methods in submission => usable methods
    protected final Map<MethodHeader, MethodHeader> superClassConstructors = new HashMap<>();

    /**
     * Initializes a new {@link ClassInfo} object.
     *
     * @param transformationContext the transformation context
     */
    public ClassInfo(TransformationContext transformationContext) {
        super(Opcodes.ASM9);

        this.transformationContext = transformationContext;
    }

    /**
     * Returns the original class header.
     *
     * @return the original class header
     */
    public abstract ClassHeader getOriginalClassHeader();

    /**
     * Returns the computed class header.
     * The computed header is the header of the associated solution class, if one is present.
     * If no solution class is present, the computed header equals the original submission class header.
     *
     * @return the computed class header
     */
    public abstract ClassHeader getComputedClassHeader();

    /**
     * Returns the original field headers for this class.
     *
     * @return the original field headers
     */
    public abstract Set<FieldHeader> getOriginalFieldHeaders();

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
    public abstract FieldHeader getComputedFieldHeader(String name);

    /**
     * Return the original method headers for this class.
     *
     * @return the original method headers
     */
    public abstract Set<MethodHeader> getOriginalMethodHeaders();

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
    public abstract MethodHeader getComputedMethodHeader(String name, String descriptor);

    /**
     * Returns the original method headers of the direct superclass' constructors.
     *
     * @return the original superclass constructor headers
     */
    public abstract Set<MethodHeader> getOriginalSuperClassConstructorHeaders();

    /**
     * Returns the computed superclass constructor header for the given method descriptor.
     * If the direct superclass is part of the submission and has a corresponding solution class,
     * the computed header is the constructor header of the solution class.
     * Otherwise, it is the original constructor header.
     *
     * @param descriptor the constructor descriptor
     * @return the computed constructor header
     */
    public abstract MethodHeader getComputedSuperClassConstructorHeader(String descriptor);

    /**
     * Recursively resolves all relevant members of the given type.
     *
     * @param superTypeMembers a set for recording type members
     * @param typeName         the name of the class / interface to process
     */
    protected abstract void resolveSuperTypeMembers(Set<SuperTypeMembers> superTypeMembers, String typeName);

    /**
     * Recursively resolves the members of superclasses and interfaces.
     *
     * @param superTypeMembers a set for recording type members
     * @param superClass       the name of the superclass to process
     * @param interfaces       the names of the interfaces to process
     */
    protected void resolveSuperTypeMembers(Set<SuperTypeMembers> superTypeMembers, String superClass, String[] interfaces) {
        resolveSuperTypeMembers(superTypeMembers, superClass);
        if (interfaces != null) {
            for (String interfaceName : interfaces) {
                resolveSuperTypeMembers(superTypeMembers, interfaceName);
            }
        }
    }

    /**
     * Resolves the members of types that are neither submission classes nor solution classes.
     *
     * @param superTypeMembers a set for recording type members
     * @param typeName         the name of the type to process
     * @param recursive        whether to recursively resolve superclass and interfaces of the given type
     */
    protected void resolveExternalSuperTypeMembers(Set<SuperTypeMembers> superTypeMembers, String typeName, boolean recursive) {
        try {
            Class<?> clazz = Class.forName(typeName.replace('/', '.'));
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
            superTypeMembers.add(new SuperTypeMembers(typeName, fieldHeaders, methodHeaders));
            if (clazz.getSuperclass() != null && recursive) {
                resolveSuperTypeMembers(superTypeMembers,
                    Type.getInternalName(clazz.getSuperclass()),
                    Arrays.stream(clazz.getInterfaces()).map(Type::getInternalName).toArray(String[]::new));
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public record SuperTypeMembers(String typeName, Map<FieldHeader, FieldHeader> fields, Map<MethodHeader, MethodHeader> methods) {}
}
