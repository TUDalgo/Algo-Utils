package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.Opcodes;
import org.tudalgo.algoutils.transform.SubmissionExecutionHandler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/**
 * This class holds information about the context of an invocation.
 * Context means the instance the method was invoked on and the parameters it was invoked with
 * as well as the stack trace up to the point of invocation.
 *
 * @author Daniel Mangold
 */
@SuppressWarnings("unused")
public class Invocation {

    private final Class<?> declaringClass;
    private final MethodHeader methodHeader;
    private final StackTraceElement[] stackTrace;
    private final Object instance;
    private final List<Object> parameterValues = new ArrayList<>();

    /**
     * Constructs a new invocation (static variant).
     *
     * @param declaringClass the target method for this invocation
     * @param stackTrace   the stack trace up to the point of invocation
     */
    public Invocation(Class<?> declaringClass, MethodHeader methodHeader, StackTraceElement[] stackTrace) {
        this(declaringClass, methodHeader, stackTrace, null);
    }

    /**
     * Constructs a new invocation (non-static variant).
     *
     * @param declaringClass the target method for this invocation
     * @param stackTrace   the stack trace up to the point of invocation
     * @param instance     the object on which this invocation takes place
     */
    public Invocation(Class<?> declaringClass, MethodHeader methodHeader, StackTraceElement[] stackTrace, Object instance) {
        this.declaringClass = declaringClass;
        this.methodHeader = methodHeader;
        this.stackTrace = new StackTraceElement[stackTrace.length - 1];
        System.arraycopy(stackTrace, 1, this.stackTrace, 0, stackTrace.length - 1);
        this.instance = instance;
    }

    /**
     * Returns the object the method was invoked on.
     *
     * @return the object the method was invoked on.
     */
    @SuppressWarnings("unchecked")
    public <T> T getInstance() {
        return (T) instance;
    }

    /**
     * Returns the object the method was invoked on.
     *
     * @param clazz the class the instance will be cast to
     * @return the object the method was invoked on
     */
    public <T> T getInstance(Class<T> clazz) {
        return clazz.cast(instance);
    }

    /**
     * Returns the stack trace up to the point of this method's invocation.
     *
     * @return the stack trace
     */
    public StackTraceElement[] getStackTrace() {
        return stackTrace;
    }

    /**
     * Returns the stack trace element of the caller.
     *
     * @return the stack trace element
     */
    public StackTraceElement getCallerStackTraceElement() {
        return stackTrace[0];
    }

    /**
     * Returns the list of parameter values the method was invoked with.
     *
     * @return the list of parameter values the method was invoked with.
     */
    public List<Object> getParameters() {
        return Collections.unmodifiableList(parameterValues);
    }

    /**
     * Returns the value of the parameter at the given index.
     *
     * @param index the parameter's index
     * @return the parameter value
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(int index) {
        return (T) parameterValues.get(index);
    }

    /**
     * Returns the value of the parameter at the given index, cast to the given class.
     *
     * @param index the parameter's index
     * @param clazz the class the value will be cast to
     * @return the parameter value, cast to the given class
     */
    public <T> T getParameter(int index, Class<T> clazz) {
        return clazz.cast(parameterValues.get(index));
    }

    /**
     * Returns the value of the {@code boolean} parameter at the given index.
     *
     * @param index the parameter's index
     * @return the parameter value
     */
    public boolean getBooleanParameter(int index) {
        return getParameter(index, Boolean.class);
    }

    /**
     * Returns the value of the {@code byte} parameter at the given index.
     *
     * @param index the parameter's index
     * @return the parameter value
     */
    public byte getByteParameter(int index) {
        return getParameter(index, Byte.class);
    }

    /**
     * Returns the value of the {@code short} parameter at the given index.
     *
     * @param index the parameter's index
     * @return the parameter value
     */
    public short getShortParameter(int index) {
        return getParameter(index, Short.class);
    }

    /**
     * Returns the value of the {@code char} parameter at the given index.
     *
     * @param index the parameter's index
     * @return the parameter value
     */
    public char getCharParameter(int index) {
        return getParameter(index, Character.class);
    }

    /**
     * Returns the value of the {@code int} parameter at the given index.
     *
     * @param index the parameter's index
     * @return the parameter value
     */
    public int getIntParameter(int index) {
        return getParameter(index, Integer.class);
    }

    /**
     * Returns the value of the {@code long} parameter at the given index.
     *
     * @param index the parameter's index
     * @return the parameter value
     */
    public long getLongParameter(int index) {
        return getParameter(index, Long.class);
    }

    /**
     * Returns the value of the {@code float} parameter at the given index.
     *
     * @param index the parameter's index
     * @return the parameter value
     */
    public float getFloatParameter(int index) {
        return getParameter(index, Float.class);
    }

    /**
     * Returns the value of the {@code double} parameter at the given index.
     *
     * @param index the parameter's index
     * @return the parameter value
     */
    public double getDoubleParameter(int index) {
        return getParameter(index, Double.class);
    }

    /**
     * Adds a parameter value to the list of values.
     *
     * @param value the value to add
     */
    public void addParameter(Object value) {
        parameterValues.add(value);
    }

    /**
     * Calls the original method with the stored parameter values.
     *
     * @param delegate whether to use the solution (delegated) or submission class implementation (not delegated)
     * @return the value returned the original method
     */
    public Object callOriginalMethod(boolean delegate) {
        return callOriginalMethod(delegate, parameterValues.toArray());
    }

    /**
     * Calls the original method with the given parameter values.
     *
     * @param delegate whether to use the solution (delegated) or submission class implementation (not delegated)
     * @param params   the values to invoke the original method with
     * @return the value returned the original method
     */
    public Object callOriginalMethod(boolean delegate, Object... params) {
        Object[] invocationArgs;
        if (instance != null) {
            invocationArgs = new Object[params.length + 1];
            invocationArgs[0] = instance;
            System.arraycopy(params, 0, invocationArgs, 1, params.length);
        } else {
            invocationArgs = params;
        }

        SubmissionExecutionHandler executionHandler = SubmissionExecutionHandler.getInstance();
        SubmissionExecutionHandler.Internal sehInternal = executionHandler.new Internal();
        MethodSubstitution methodSubstitution = sehInternal.getSubstitution(methodHeader);
        executionHandler.disableMethodSubstitution(methodHeader);
        boolean isDelegated = !sehInternal.useSubmissionImpl(methodHeader);
        if (delegate) {
            executionHandler.enableMethodDelegation(methodHeader);
        } else {
            executionHandler.disableMethodDelegation(methodHeader);
        }

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType methodType = MethodType.fromMethodDescriptorString(methodHeader.descriptor(), getClass().getClassLoader());
            MethodHandle methodHandle = switch (methodHeader.getOpcode()) {
                case Opcodes.INVOKEVIRTUAL -> lookup.findVirtual(declaringClass, methodHeader.name(), methodType);
                case Opcodes.INVOKESPECIAL -> lookup.findConstructor(declaringClass, methodType);
                case Opcodes.INVOKESTATIC -> lookup.findStatic(declaringClass, methodHeader.name(), methodType);
                default -> throw new IllegalArgumentException("Unsupported opcode: " + methodHeader.getOpcode());
            };
            return methodHandle.invoke(invocationArgs);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            executionHandler.substituteMethod(methodHeader, methodSubstitution);
            if (isDelegated) {
                executionHandler.enableMethodDelegation(methodHeader);
            } else {
                executionHandler.disableMethodDelegation(methodHeader);
            }
        }
    }

    @Override
    public String toString() {
        return "Invocation{instance=%s, parameterValues=%s, stackTrace=%s}".formatted(instance, parameterValues, Arrays.toString(stackTrace));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Invocation that = (Invocation) o;
        return Objects.equals(instance, that.instance) &&
            Arrays.equals(stackTrace, that.stackTrace) &&
            Objects.equals(parameterValues, that.parameterValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instance, Arrays.hashCode(stackTrace), parameterValues);
    }
}
