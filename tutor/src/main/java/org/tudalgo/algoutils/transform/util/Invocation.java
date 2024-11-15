package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * This class holds information about the context of an invocation.
 * Context means the object a method was invoked on and the parameters it was invoked with.
 */
public class Invocation {

    public static final Type INTERNAL_TYPE = Type.getType(Invocation.class);

    private final Object instance;
    private final List<Object> parameterValues = new ArrayList<>();

    /**
     * Constructs a new invocation.
     */
    public Invocation() {
        this(null);
    }

    /**
     * Constructs a new invocation.
     *
     * @param instance the object on which this invocation takes place
     */
    public Invocation(Object instance) {
        this.instance = instance;
    }

    /**
     * Returns the object the method was invoked on.
     *
     * @return the object the method was invoked on.
     */
    public Object getInstance() {
        return instance;
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
        return "Invocation{instance=%s, parameterValues=%s}".formatted(instance, parameterValues);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Invocation that = (Invocation) o;
        return Objects.equals(parameterValues, that.parameterValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterValues);
    }
}
