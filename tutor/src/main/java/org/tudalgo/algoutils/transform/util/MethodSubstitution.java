package org.tudalgo.algoutils.transform.util;

import org.objectweb.asm.Type;

/**
 * This functional interface represents a substitution for a method.
 * The functional method {@link #execute(Invocation)} is called with the original invocation's context.
 * Its return value is also the value that will be returned by the substituted method.
 */
@FunctionalInterface
public interface MethodSubstitution {

    /**
     * Defines the behaviour of method substitution when the substituted method is a constructor.
     * When a constructor method is substituted, either {@code super(...)} or {@code this(...)} must be called
     * before calling {@link #execute(Invocation)}.
     * This method returns a {@link ConstructorInvocation} object storing...
     * <ol>
     *     <li>the internal class name / owner of the target constructor and</li>
     *     <li>the values that are passed to the constructor of that class.</li>
     * </ol>
     * The owner must equal either the class whose constructor is substituted or its superclass.
     * Default behaviour assumes calling the constructor of {@link Object}, i.e.,
     * a class that has no explicit superclass.
     *
     * @return a record containing the target method's owner and arguments
     */
    default ConstructorInvocation getConstructorInvocation() {
        return new ConstructorInvocation("java/lang/Object", "()V");
    }

    /**
     * Defines the actions of the substituted method.
     *
     * @param invocation the context of an invocation
     * @return the return value of the substituted method
     */
    Object execute(Invocation invocation);

    /**
     * A record storing the internal name of the class / owner of the target constructor, the constructor descriptor
     * (see <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3">Chapter 4.3</a> of the JLS)
     * and the arguments it is invoked with.
     *
     * @param owner      the internal name of the target constructor's owner (see {@link Type#getInternalName()})
     * @param descriptor the descriptor of the target constructor
     * @param args       the arguments the constructor will be invoked with
     */
    record ConstructorInvocation(String owner, String descriptor, Object... args) {}
}
