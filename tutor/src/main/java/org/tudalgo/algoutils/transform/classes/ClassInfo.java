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

public abstract class ClassInfo extends ClassVisitor {

    protected final TransformationContext transformationContext;
    protected final Set<SuperTypeMembers> superTypeMembers = new HashSet<>();

    protected final Map<FieldHeader, FieldHeader> fields = new HashMap<>(); // Mapping of fields in submission => usable fields
    protected final Map<MethodHeader, MethodHeader> methods = new HashMap<>(); // Mapping of methods in submission => usable methods
    protected final Map<MethodHeader, MethodHeader> superClassConstructors = new HashMap<>();

    public ClassInfo(TransformationContext transformationContext) {
        super(Opcodes.ASM9);

        this.transformationContext = transformationContext;
    }

    public abstract ClassHeader getOriginalClassHeader();

    public abstract ClassHeader getComputedClassHeader();

    public abstract Set<FieldHeader> getOriginalFieldHeaders();

    public abstract FieldHeader getComputedFieldHeader(String name);

    public abstract Set<MethodHeader> getOriginalMethodHeaders();

    public abstract MethodHeader getComputedMethodHeader(String name, String descriptor);

    public abstract Set<MethodHeader> getOriginalSuperClassConstructorHeaders();

    public abstract MethodHeader getComputedSuperClassConstructorHeader(String descriptor);

    protected abstract void resolveSuperTypeMembers(Set<SuperTypeMembers> superTypeMembers, String typeName);

    /**
     * Recursively resolves the members of superclasses and interfaces.
     *
     * @param superTypeMembers a set for recording class members
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
