package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.Type;

/**
 * Common interface of all header records.
 */
public interface Header {

    /**
     * Returns the type for this header.
     *
     * @return the type for this header
     */
    Type getType();

    /**
     * Returns the parameter types for this record's primary constructor.
     *
     * @return the parameter types
     */
    Type[] getConstructorParameterTypes();

    /**
     * Returns the stored value for the given record component's name.
     *
     * @param name the name of the record component
     * @return the record component's value
     */
    Object getValue(String name);
}
