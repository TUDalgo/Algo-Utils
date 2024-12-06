package org.tudalgo.algoutils.transform.util.headers;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.tudalgo.algoutils.transform.util.Constants;
import org.tudalgo.algoutils.transform.util.TransformationUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A record holding information on the header of a class as declared in Java bytecode.
 * {@code name}, {@code superName} as well as the values of {@code interfaces} use the internal name
 * of the corresponding class (see {@link Type#getInternalName()}).
 *
 * @param access     the class' modifiers
 * @param name       the class' name
 * @param signature  the class' signature, if using type parameters
 * @param superName  the class' superclass
 * @param interfaces the class' interfaces
 * @author Daniel Mangold
 */
public record ClassHeader(int access, String name, String signature, String superName, String[] interfaces) implements Header {

    @Override
    public Type getHeaderType() {
        return Constants.CLASS_HEADER_TYPE;
    }

    @Override
    public HeaderRecordComponent[] getComponents() {
        return new HeaderRecordComponent[] {
            new HeaderRecordComponent(Type.INT_TYPE, access),
            new HeaderRecordComponent(Constants.STRING_TYPE, name),
            new HeaderRecordComponent(Constants.STRING_TYPE, signature),
            new HeaderRecordComponent(Constants.STRING_TYPE, superName),
            new HeaderRecordComponent(Constants.STRING_ARRAY_TYPE, interfaces)
        };
    }

    /**
     * Visits the class header using the information stored in this record.
     *
     * @param delegate             the class visitor to use
     * @param version              the class version (see {@link ClassVisitor#visit(int, int, String, String, String, String[])})
     * @param additionalInterfaces the internal names of additional interfaces this class should implement
     */
    public void visitClass(ClassVisitor delegate, int version, String... additionalInterfaces) {
        String[] interfaces;
        if (this.interfaces == null) {
            interfaces = additionalInterfaces;
        } else {
            interfaces = new String[this.interfaces.length + additionalInterfaces.length];
            System.arraycopy(this.interfaces, 0, interfaces, 0, this.interfaces.length);
            System.arraycopy(additionalInterfaces, 0, interfaces, this.interfaces.length, additionalInterfaces.length);
        }

        delegate.visit(version, this.access, this.name, this.signature, this.superName, interfaces);
    }

    /**
     * Returns a new class header describing the given class.
     *
     * @param clazz the class
     * @return the new class header object
     */
    public static ClassHeader of(Class<?> clazz) {
        return new ClassHeader(clazz.getModifiers(),
            Type.getInternalName(clazz),
            null,
            clazz.getSuperclass() != null ? Type.getInternalName(clazz.getSuperclass()) : null,
            Arrays.stream(clazz.getInterfaces()).map(Type::getInternalName).toArray(String[]::new));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassHeader that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        String signatureString = "(signature: '%s')".formatted(signature);
        String superClassString = "extends %s".formatted(superName != null ? superName.replace('/', '.') : "");
        String interfacesString = "implements %s".formatted(Arrays.stream(interfaces == null ? new String[0] : interfaces)
            .map(s -> s.replace('/', '.'))
            .collect(Collectors.joining(", ")));
        return "%s %s %s %s %s".formatted(
            TransformationUtils.toHumanReadableModifiers(access) +
                (((Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_RECORD) & access) == 0 ? " class" : ""),
            TransformationUtils.toHumanReadableType(Type.getObjectType(name)),
            superName != null ? superClassString : "",
            interfaces != null && interfaces.length > 0 ? interfacesString : "",
            signature != null ? signatureString : ""
        ).trim();
    }
}
