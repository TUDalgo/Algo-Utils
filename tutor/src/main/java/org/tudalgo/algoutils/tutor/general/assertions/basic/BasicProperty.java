package org.tudalgo.algoutils.tutor.general.assertions.basic;

import org.tudalgo.algoutils.tutor.general.assertions.Property;

import java.util.Objects;

/**
 * <p>A basic implementation of a property. </p>
 *
 * @param key   the key of the property
 * @param value the value of the property
 */
public record BasicProperty(
    String key,
    Object value
) implements Property {

    public BasicProperty {
        // Test validity when creating so that a faulty property gets spotted early.
        if(value.getClass().isArray()) {
            // Get most inner array type
            Class<?> elementType = value.getClass().getComponentType();
            while(elementType.isArray()) {
                elementType = elementType.getComponentType();
            }
            if(elementType.isPrimitive()) {
                if(elementType != int.class && elementType != long.class && elementType != double.class)
                    throw new IllegalArgumentException(
                        "Context Property only supports arrays of types int, long, double, Object");
            }
        }
    }

    /**
     * <p>Returns true iff the object is a property with the same key. </p>
     *
     * @param o the reference object with which to compare.
     * @return true iff the object is a property with the same key
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BasicProperty that = (BasicProperty) o;
        return Objects.equals(key, that.key);
    }
}
