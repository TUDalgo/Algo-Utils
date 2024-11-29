package org.tudalgo.algoutils.transform.util;

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

    private final Object instance;
    private final StackTraceElement[] stackTrace;
    private final List<Object> parameterValues = new ArrayList<>();

    /**
     * Constructs a new invocation.
     *
     * @param stackTrace the stack trace up to the point of invocation
     */
    public Invocation(StackTraceElement[] stackTrace) {
        this(null, stackTrace);
    }

    /**
     * Constructs a new invocation.
     *
     * @param instance   the object on which this invocation takes place
     * @param stackTrace the stack trace up to the point of invocation
     */
    public Invocation(Object instance, StackTraceElement[] stackTrace) {
        this.instance = instance;
        this.stackTrace = new StackTraceElement[stackTrace.length - 1];
        System.arraycopy(stackTrace, 1, this.stackTrace, 0, stackTrace.length - 1);
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
