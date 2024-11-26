package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A record holding information on the header of a field as declared in java bytecode.
 * {@code owner} uses the internal name of the corresponding class (see {@link Type#getInternalName()}).
 * {@link Type#getType(String)} can be used with {@code descriptor} to get a more user-friendly
 * representation of this field's type.
 *
 * @param owner      the field's owner or declaring class
 * @param access     the field's modifiers
 * @param name       the field's name
 * @param descriptor the field's descriptor / type
 * @param signature  the field's signature, if using type parameters
 * @author Daniel Mangold
 */
public record FieldHeader(String owner, int access, String name, String descriptor, String signature) implements Header {

    public FieldHeader(Field field) {
        this(Type.getInternalName(field.getDeclaringClass()),
            field.getModifiers(),
            field.getName(),
            Type.getDescriptor(field.getType()),
            null);
    }

    @Override
    public Type getType() {
        return Constants.FIELD_HEADER_TYPE;
    }

    @Override
    public Type[] getConstructorParameterTypes() {
        return Constants.FIELD_HEADER_CONSTRUCTOR_TYPES;
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
        return delegate.visitField(access & ~Opcodes.ACC_FINAL, name, descriptor, signature, value);
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

    @Override
    public String toString() {
        String signatureString = "(signature: '%s')".formatted(signature);
        return "%s %s %s#%s %s".formatted(
            TransformationUtils.toHumanReadableModifiers(access),
            TransformationUtils.toHumanReadableType(Type.getType(descriptor)),
            TransformationUtils.toHumanReadableType(Type.getObjectType(owner)),
            name,
            signature != null ? signatureString : ""
        ).trim();
    }
}
