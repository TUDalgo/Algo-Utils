package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Type;

import java.util.Objects;

/**
 * A record holding information on the header of a field as declared in java bytecode.
 *
 * @param owner      the field's owner or declaring class
 * @param access     the field's modifiers
 * @param name       the field's name
 * @param descriptor the field's descriptor / type
 * @param signature  the field's signature, if using type parameters
 * @author Daniel Mangold
 */
public record FieldHeader(String owner, int access, String name, String descriptor, String signature) implements Header {

    private static final Type INTERNAL_TYPE = Type.getType(FieldHeader.class);
    private static final Type[] INTERNAL_CONSTRUCTOR_TYPES = new Type[] {
        Type.getType(String.class),
        Type.INT_TYPE,
        Type.getType(String.class),
        Type.getType(String.class),
        Type.getType(String.class)
    };

    @Override
    public Type getType() {
        return INTERNAL_TYPE;
    }

    @Override
    public Type[] getConstructorParameterTypes() {
        return INTERNAL_CONSTRUCTOR_TYPES;
    }

    @Override
    public Object getValue(String name) {
        return switch (name) {
            case "owner" -> this.owner;
            case "access" -> this.access;
            case "name" -> this.name;
            case "descriptor" -> this.descriptor;
            case "signature" -> this.signature;
            default -> throw new IllegalArgumentException("Invalid name: " + name);
        };
    }

    /**
     * Visits a field in the given class visitor using the information stored in this record.
     *
     * @param delegate the class visitor to use
     * @param value    an optional value for static fields
     *                 (see {@link ClassVisitor#visitField(int, String, String, String, Object)})
     * @return the resulting {@link FieldVisitor}
     */
    public FieldVisitor toFieldVisitor(ClassVisitor delegate, Object value) {
        return delegate.visitField(access, name, descriptor, signature, value);
    }

    /**
     * Two instances of {@link FieldHeader} are considered equal if their names are equal.
     * TODO: include owner and parent classes if possible
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldHeader that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
