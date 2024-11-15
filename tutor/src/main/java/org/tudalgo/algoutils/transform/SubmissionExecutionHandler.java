package org.tudalgo.algoutils.transform;

import org.tudalgo.algoutils.transform.util.*;
import kotlin.Pair;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A singleton class to configure the way a submission is executed.
 * This class can be used to
 * <ul>
 *     <li>log method invocations</li>
 *     <li>delegate invocations to the solution / a pre-defined external class</li>
 *     <li>delegate invocations to a custom programmatically-defined method (e.g. lambdas)</li>
 * </ul>
 * By default, all method calls are delegated to the solution class, if one is present.
 * To call the real method, delegation must be disabled before calling it.
 * This can be done either explicitly using {@link #disableMethodDelegation} or implicitly using
 * {@link #substituteMethod}.
 * <br>
 * To use any of these features, the submission classes need to be transformed by {@link SolutionMergingClassTransformer}.
 * <br><br>
 * An example test class could look like this:
 * <pre>
 * {@code
 * public class ExampleTest {
 *
 *     private final SubmissionExecutionHandler executionHandler = SubmissionExecutionHandler.getInstance();
 *
 *     @BeforeAll
 *     public static void start() {
 *         Utils.transformSubmission(); // In case Jagr is not present
 *     }
 *
 *     @BeforeEach
 *     public void setup() {
 *         // Pre-test setup, if necessary. Useful for substitution:
 *         Method substitutedMethod = TestedClass.class.getDeclaredMethod("dependencyForTest");
 *         executionHandler.substituteMethod(substitutedMethod, invocation -> "Hello world!");
 *     }
 *
 *     @AfterEach
 *     public void reset() {
 *         // Optionally reset invocation logs, substitutions, etc.
 *         executionHandler.resetMethodInvocationLogging();
 *         executionHandler.resetMethodDelegation();
 *         executionHandler.resetMethodSubstitution();
 *     }
 *
 *     @Test
 *     public void test() throws ReflectiveOperationException {
 *         Method method = TestedClass.class.getDeclaredMethod("methodUnderTest");
 *         executionHandler.disableDelegation(method); // Disable delegation, i.e., use the original implementation
 *         ...
 *     }
 * }
 * }
 * </pre>
 *
 * @see SolutionMergingClassTransformer
 * @see SubmissionClassVisitor
 * @author Daniel Mangold
 */
@SuppressWarnings("unused")
public class SubmissionExecutionHandler {

    public static final Type INTERNAL_TYPE = Type.getType(SubmissionExecutionHandler.class);

    private static SubmissionExecutionHandler instance;

    // declaring class => (method header => invocations)
    private final Map<String, Map<MethodHeader, List<Invocation>>> methodInvocations = new HashMap<>();
    private final Map<String, Map<MethodHeader, MethodSubstitution>> methodSubstitutions = new HashMap<>();
    private final Map<String, Map<MethodHeader, Boolean>> methodDelegationAllowlist = new HashMap<>();

    private SubmissionExecutionHandler() {}

    /**
     * Returns the global {@link SubmissionExecutionHandler} instance.
     *
     * @return the global {@link SubmissionExecutionHandler} instance
     * @throws IllegalStateException if no global instance is present, i.e., the project has not been
     *         transformed by {@link SolutionMergingClassTransformer}
     */
    public static SubmissionExecutionHandler getInstance() {
        if (instance == null) {
            instance = new SubmissionExecutionHandler();
        }
        return instance;
    }

    // Submission class info

    /**
     * Returns the original class header for the given submission class.
     *
     * @param clazz the submission class
     * @return the original class header
     */
    public static ClassHeader getOriginalClassHeader(Class<?> clazz) {
        try {
            return (ClassHeader) MethodHandles.lookup()
                .findStatic(clazz, "getOriginalClassHeader", MethodType.methodType(ClassHeader.class))
                .invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the set of original field headers for the given submission class.
     *
     * @param clazz the submission class
     * @return the set of original field headers
     */
    @SuppressWarnings("unchecked")
    public static Set<FieldHeader> getOriginalFieldHeaders(Class<?> clazz) {
        try {
            return (Set<FieldHeader>) MethodHandles.lookup()
                .findStatic(clazz, "getOriginalFieldHeaders", MethodType.methodType(Set.class))
                .invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the set of original method headers for the given submission class.
     *
     * @param clazz the submission class
     * @return the set of original method headers
     */
    @SuppressWarnings("unchecked")
    public static Set<MethodHeader> getOriginalMethodHeaders(Class<?> clazz) {
        try {
            return (Set<MethodHeader>) MethodHandles.lookup()
                .findStatic(clazz, "getOriginalMethodHeaders", MethodType.methodType(Set.class))
                .invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Invocation logging

    /**
     * Resets the logging of method invocations to log no invocations.
     */
    public void resetMethodInvocationLogging() {
        methodInvocations.clear();
    }

    /**
     * Enables logging of method invocations for the given method.
     *
     * @param method the method to enable invocation logging for
     */
    public void enableMethodInvocationLogging(Method method) {
        enableMethodInvocationLogging(new MethodHeader(method));
    }

    /**
     * Enables logging of method invocations for the given method.
     *
     * @param methodHeader a method header describing the method
     */
    public void enableMethodInvocationLogging(MethodHeader methodHeader) {
        methodInvocations.computeIfAbsent(methodHeader.owner(), k -> new HashMap<>())
            .putIfAbsent(methodHeader, new ArrayList<>());
    }

    /**
     * Returns all logged invocations for the given method.
     *
     * @param method the method to get invocations of
     * @return a list of invocations on the given method
     */
    public List<Invocation> getInvocationsForMethod(Method method) {
        return getInvocationsForMethod(new MethodHeader(method));
    }

    /**
     * Returns all logged invocations for the given method.
     *
     * @param methodHeader a method header describing the method
     * @return a list of invocations on the given method
     */
    public List<Invocation> getInvocationsForMethod(MethodHeader methodHeader) {
        return Optional.ofNullable(methodInvocations.get(methodHeader.owner()))
            .map(map -> map.get(methodHeader))
            .map(Collections::unmodifiableList)
            .orElse(null);
    }

    // Method substitution

    /**
     * Resets the substitution of methods.
     */
    public void resetMethodSubstitution() {
        methodSubstitutions.clear();
    }

    /**
     * Substitute calls to the given method with the invocation of the given {@link MethodSubstitution}.
     * In other words, instead of executing the instructions of either the original submission or the solution,
     * this can be used to make the method do and return anything during runtime.
     *
     * @param method     the method to substitute
     * @param substitute the {@link MethodSubstitution} the method will be substituted with
     */
    public void substituteMethod(Method method, MethodSubstitution substitute) {
        substituteMethod(new MethodHeader(method), substitute);
    }

    /**
     * Substitute calls to the given method with the invocation of the given {@link MethodSubstitution}.
     * In other words, instead of executing the instructions of either the original submission or the solution,
     * this can be used to make the method do and return anything during runtime.
     *
     * @param methodHeader a method header describing the method
     * @param substitute   the {@link MethodSubstitution} the method will be substituted with
     */
    public void substituteMethod(MethodHeader methodHeader, MethodSubstitution substitute) {
        methodSubstitutions.computeIfAbsent(methodHeader.owner(), k -> new HashMap<>())
            .put(methodHeader, substitute);
    }

    // Method delegation

    /**
     * Resets the delegation of methods.
     */
    public void resetMethodDelegation() {
        methodDelegationAllowlist.clear();
    }

    /**
     * Disables delegation to the solution for the given method.
     *
     * @param method the method to disable delegation for
     */
    public void disableMethodDelegation(Method method) {
        disableMethodDelegation(new MethodHeader(method));
    }

    /**
     * Disables delegation to the solution for the given method.
     *
     * @param methodHeader a method header describing the method
     */
    public void disableMethodDelegation(MethodHeader methodHeader) {
        methodDelegationAllowlist.computeIfAbsent(methodHeader.owner(), k -> new HashMap<>())
            .put(methodHeader, true);
    }

    /**
     * This functional interface represents a substitution for a method.
     * The functional method {@link #execute(Invocation)} is called with the original invocation's context.
     * Its return value is also the value that will be returned by the substituted method.
     */
    @FunctionalInterface
    public interface MethodSubstitution {

        Type INTERNAL_TYPE = Type.getType(MethodSubstitution.class);

        /**
         * DO NOT USE, THIS METHOD HAS NO EFFECT RIGHT NOW.
         * TODO: implement constructor substitution
         * <br><br>
         * Defines the behaviour of method substitution when the substituted method is a constructor.
         * When a constructor method is substituted, either {@code super(...)} or {@code this(...)} must be called
         * before calling {@link #execute(Invocation)}.
         * This method returns a pair consisting of...
         * <ol>
         *     <li>the internal class name / owner of the target constructor and</li>
         *     <li>the values that are passed to the constructor of that class.</li>
         * </ol>
         * The first pair entry must be either the original method's owner (for {@code this(...)}) or
         * the superclass (for {@code super(...)}).
         * The second entry is an array of parameter values for that constructor.
         * Default behaviour assumes calling the constructor of {@link Object}, i.e., a class that has no superclass.
         *
         * @return a pair containing the target method's owner and arguments
         */
        default Pair<String, Object[]> constructorBehaviour() {
            return new Pair<>("java/lang/Object", new Object[0]);
        }

        /**
         * Defines the actions of the substituted method.
         *
         * @param invocation the context of an invocation
         * @return the return value of the substituted method
         */
        Object execute(Invocation invocation);
    }

    /**
     * Collection of methods injected into the bytecode of transformed methods.
     */
    public final class Internal {

        public static final Type INTERNAL_TYPE = Type.getType(Internal.class);

        // Invocation logging

        /**
         * Returns whether the calling method's invocation is logged, i.e.
         * {@link #addInvocation(MethodHeader, Invocation)} may be called or not.
         * Should only be used in bytecode transformations when intercepting method invocations.
         *
         * @param methodHeader a method header describing the method
         * @return {@code true} if invocation logging is enabled for the given method, otherwise {@code false}
         */
        public boolean logInvocation(MethodHeader methodHeader) {
            return Optional.ofNullable(methodInvocations.get(methodHeader.owner()))
                .map(map -> map.get(methodHeader))
                .isPresent();
        }

        /**
         * Adds an invocation to the list of invocations for the calling method.
         * Should only be used in bytecode transformations when intercepting method invocations.
         *
         * @param methodHeader a method header describing the method
         * @param invocation the invocation on the method, i.e. the context it has been called with
         */
        public void addInvocation(MethodHeader methodHeader, Invocation invocation) {
            Optional.ofNullable(methodInvocations.get(methodHeader.owner()))
                .map(map -> map.get(methodHeader))
                .ifPresent(list -> list.add(invocation));
        }

        // Method substitution

        /**
         * Returns whether the given method has a substitute or not.
         * Should only be used in bytecode transformations when intercepting method invocations.
         *
         * @param methodHeader a method header describing the method
         * @return {@code true} if substitution is enabled for the given method, otherwise {@code false}
         */
        public boolean useSubstitution(MethodHeader methodHeader) {
            return Optional.ofNullable(methodSubstitutions.get(methodHeader.owner()))
                .map(map -> map.containsKey(methodHeader))
                .orElse(false);
        }

        /**
         * Returns the substitute for the given method.
         * Should only be used in bytecode transformations when intercepting method invocations.
         *
         * @param methodHeader a method header describing the method
         * @return the substitute for the given method
         */
        public MethodSubstitution getSubstitution(MethodHeader methodHeader) {
            return Optional.ofNullable(methodSubstitutions.get(methodHeader.owner()))
                .map(map -> map.get(methodHeader))
                .orElseThrow();
        }

        // Method delegation

        /**
         * Returns whether the original instructions are used or not.
         * Should only be used in bytecode transformations when intercepting method invocations.
         *
         * @param methodHeader a method header describing the method
         * @return {@code true} if delegation is disabled for the given method, otherwise {@code false}
         */
        public boolean useSubmissionImpl(MethodHeader methodHeader) {
            return Optional.ofNullable(methodDelegationAllowlist.get(methodHeader.owner()))
                .map(map -> map.get(methodHeader))
                .orElse(false);
        }
    }
}
