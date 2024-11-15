package org.tudalgo.algoutils.student.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Forces the annotated type or member to be mapped to the specified one.
 * Mappings must be 1:1, meaning multiple annotated members (or types) may not map to the same target and
 * all members and types may be targeted by at most one annotation.
 *
 * @author Daniel Mangold
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface ForceSignature {

    /**
     * The identifier of the annotated type / member.
     * The value must be as follows:
     * <ul>
     *     <li>Types: the fully qualified name of the type (e.g., {@code java.lang.Object})</li>
     *     <li>Fields: the name / identifier of the field</li>
     *     <li>Constructors: Always {@code <init>}, regardless of the class</li>
     *     <li>Methods: the name / identifier of the method</li>
     * </ul>
     *
     * @return the type / member identifier
     */
    String identifier();

    /**
     * The method descriptor as specified by
     * <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3">Chapter 4.3</a>
     * of the Java Virtual Machine Specification.
     * If a value is set, it takes precedence over {@link #returnType()} and {@link #parameterTypes()}.
     *
     * <p>
     *     Note: Setting this value has no effect for types or fields.
     * </p>
     *
     * @return the method's descriptor
     */
    String descriptor() default "";

    /**
     * The class object specifying the method's return type.
     * If a value is set, it will be overwritten if {@link #descriptor()} is also set.
     * Default is no return type (void).
     *
     * <p>
     *     Note: Setting this value has no effect for types or fields.
     * </p>
     *
     * @return the method's return type
     */
    Class<?> returnType() default void.class;

    /**
     * An array of class objects specifying the method's parameter types.
     * The classes need to be given in the same order as they are declared by the targeted method.
     * If a value is set, it will be overwritten if {@link #descriptor()} is also set.
     * Default is no parameters.
     *
     * <p>
     *     Note: Setting this value has no effect for types or fields.
     * </p>
     *
     * @return the method's parameter types
     */
    Class<?>[] parameterTypes() default {};
}
