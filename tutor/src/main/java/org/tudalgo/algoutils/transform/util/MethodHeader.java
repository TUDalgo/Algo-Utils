package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;

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
     * Constructs a new method header with only necessary information.
     * This method header should not invoke {@link #toMethodVisitor(ClassVisitor)},
     * {@link #toMethodInsn(MethodVisitor, boolean)} or {@link #getOpcode()}.
     *
     * @param owner      the method's owner or declaring class
     * @param name       the method's name
     * @param descriptor the method's descriptor / parameter types + return type
     */
    public MethodHeader(String owner, String name, String descriptor) {
        this(owner, 0, name, descriptor, null, null);
    }

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
    public HeaderRecordComponent[] getComponents() {
        return new HeaderRecordComponent[] {
            new HeaderRecordComponent(Constants.STRING_TYPE, owner),
            new HeaderRecordComponent(Type.INT_TYPE, access),
            new HeaderRecordComponent(Constants.STRING_TYPE, name),
            new HeaderRecordComponent(Constants.STRING_TYPE, descriptor),
            new HeaderRecordComponent(Constants.STRING_TYPE, signature),
            new HeaderRecordComponent(Constants.STRING_ARRAY_TYPE, exceptions)
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
     * Visits a method instruction in the given method visitor using the information stored in this record.
     *
     * @param methodVisitor the method visitor to use
     * @param isInterface   true, if the method's owner is an interface
     */
    public void toMethodInsn(MethodVisitor methodVisitor, boolean isInterface) {
        int opcode = isInterface ? INVOKEINTERFACE : getOpcode();
        methodVisitor.visitMethodInsn(opcode,
            owner,
            name,
            descriptor,
            isInterface);
    }

    /**
     * Returns the opcode needed to invoke this method (except INVOKEINTERFACE since it also depends on the class).
     *
     * @return the opcode
     */
    public int getOpcode() {
        if ((access & ACC_STATIC) != 0) {
            return INVOKESTATIC;
        } else if (name.equals("<init>")) {
            return INVOKESPECIAL;
        } else if (TransformationUtils.isLambdaMethod(access, name)) {
            return INVOKEDYNAMIC;
        } else {
            return INVOKEVIRTUAL;
        }
    }

    /**
     * Returns a new method header describing the specified constructor.
     *
     * @param declaringClass the class the constructor is declared in
     * @param parameterTypes the constructor's parameter types
     * @return the new method header object
     */
    public static MethodHeader of(Class<?> declaringClass, Class<?>... parameterTypes) {
        try {
            return new MethodHeader(declaringClass.getDeclaredConstructor(parameterTypes));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a new method header describing the specified method.
     * For constructors use {@link #of(Class, Class...)}.
     *
     * @param declaringClass the class the method is declared in
     * @param name           the method's name
     * @param parameterTypes the method's parameter types
     * @return the new method header object
     */
    public static MethodHeader of(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        try {
            return new MethodHeader(declaringClass.getDeclaredMethod(name, parameterTypes));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
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

    @Override
    public String toString() {
        String signatureString = "(signature: '%s')".formatted(signature);
        String exceptionsString = "throws %s".formatted(Arrays.stream(exceptions == null ? new String[0] : exceptions)
            .map(s -> s.replace('/', '.'))
            .collect(Collectors.joining(", ")));
        return "%s %s %s#%s(%s) %s %s".formatted(
            TransformationUtils.toHumanReadableModifiers(access),
            TransformationUtils.toHumanReadableType(Type.getReturnType(descriptor)),
            TransformationUtils.toHumanReadableType(Type.getObjectType(owner)),
            name,
            Arrays.stream(Type.getArgumentTypes(descriptor))
                .map(TransformationUtils::toHumanReadableType)
                .collect(Collectors.joining(", ")),
            exceptions != null && exceptions.length > 0 ? exceptionsString : "",
            signature != null ? signatureString : ""
        ).trim();
    }
}
