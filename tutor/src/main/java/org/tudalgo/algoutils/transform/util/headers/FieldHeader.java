package org.tudalgo.algoutils.transform.util.headers;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Type;
import org.tudalgo.algoutils.transform.util.Constants;
import org.tudalgo.algoutils.transform.util.TransformationUtils;

import java.lang.reflect.Field;
import java.util.Objects;

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

    /**
     * Constructs a new field header using the given field.
     *
     * @param field a java reflection field
     */
    public FieldHeader(Field field) {
        this(Type.getInternalName(field.getDeclaringClass()),
            field.getModifiers(),
            field.getName(),
            Type.getDescriptor(field.getType()),
            null);
    }

    @Override
    public Type getHeaderType() {
        return Constants.FIELD_HEADER_TYPE;
    }

    @Override
    public HeaderRecordComponent[] getComponents() {
        return new HeaderRecordComponent[] {
            new HeaderRecordComponent(Constants.STRING_TYPE, owner),
            new HeaderRecordComponent(Type.INT_TYPE, access),
            new HeaderRecordComponent(Constants.STRING_TYPE, name),
            new HeaderRecordComponent(Constants.STRING_TYPE, descriptor),
            new HeaderRecordComponent(Constants.STRING_TYPE, signature)
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
     * Returns the modifiers of this field header.
     * Alias of {@link #access()}.
     *
     * @return the modifiers of this field header
     */
    @Override
    public int modifiers() {
        return access;
    }

    /**
     * Returns a class object that identifies the declared type for the field represented by this field header.
     *
     * @return the declared type for this field header
     */
    @SuppressWarnings("unchecked")
    public <T> Class<T> getType() {
        try {
            return (Class<T>) TransformationUtils.getClassForType(Type.getType(descriptor));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a new field header describing the specified field.
     *
     * @param declaringClass the class the field is declared in
     * @param name           the field's name
     * @return the new field header object
     */
    public static FieldHeader of(Class<?> declaringClass, String name) {
        try {
            return new FieldHeader(declaringClass.getDeclaredField(name));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
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
