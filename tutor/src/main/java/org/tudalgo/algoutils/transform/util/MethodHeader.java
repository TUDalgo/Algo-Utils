package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * A record holding information on the header of a method as declared in java bytecode.
 * {@code owner} as well as the values of {@code exceptions} use the internal name
 * of the corresponding class (see {@link Type#getInternalName()}).
 * {@link Type#getMethodType(String)} can be used with {@code descriptor} to get a more user-friendly
 * representation of this method's return type and parameter types.
 *
 * @param owner      the method's owner or declaring class
 * @param access     the method's modifiers
 * @param name       the method's name
 * @param descriptor the method's descriptor / parameter types + return type
 * @param signature  the method's signature, if using type parameters
 * @param exceptions exceptions declared in the method's {@code throws} clause
 * @author Daniel Mangold
 */
public record MethodHeader(String owner, int access, String name, String descriptor, String signature, String[] exceptions) implements Header {

    /**
     * Constructs a new method header using the given method / constructor.
     *
     * @param executable a java reflection method or constructor
     */
    public MethodHeader(Executable executable) {
        this(Type.getInternalName(executable.getDeclaringClass()),
            executable.getModifiers(),
            executable instanceof Method method ? method.getName() : "<init>",
            executable instanceof Method method ?
                Type.getMethodDescriptor(method) :
                Type.getMethodDescriptor(Type.VOID_TYPE, Arrays.stream(executable.getParameterTypes())
                    .map(Type::getType)
                    .toArray(Type[]::new)),
            null,
            Arrays.stream(executable.getExceptionTypes())
                .map(Type::getInternalName)
                .toArray(String[]::new));
    }

    @Override
    public Type getType() {
        return Constants.METHOD_HEADER_TYPE;
    }

    @Override
    public Type[] getConstructorParameterTypes() {
        return Constants.METHOD_HEADER_CONSTRUCTOR_TYPES;
    }

    @Override
    public Object getValue(String name) {
        return switch (name) {
            case "owner" -> this.owner;
            case "access" -> this.access;
            case "name" -> this.name;
            case "descriptor" -> this.descriptor;
            case "signature" -> this.signature;
            case "exceptions" -> this.exceptions;
            default -> throw new IllegalArgumentException("Invalid name: " + name);
        };
    }

    /**
     * Visits a method in the given class visitor using the information stored in this record.
     *
     * @param delegate the class visitor to use
     * @return the resulting {@link MethodVisitor}
     */
    public MethodVisitor toMethodVisitor(ClassVisitor delegate) {
        return delegate.visitMethod(access, name, descriptor, signature, exceptions);
    }

    /**
     * Two instances of {@link MethodHeader} are considered equal if their names and descriptors are equal.
     * TODO: include owner and parent classes if possible
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodHeader that)) return false;
        return Objects.equals(name, that.name) && Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, descriptor);
    }
}
