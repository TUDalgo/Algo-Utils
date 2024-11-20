package org.tudalgo.algoutils.transform;

import org.tudalgo.algoutils.transform.util.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
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
                .findStatic(clazz, Constants.INJECTED_GET_ORIGINAL_CLASS_HEADER.name(), MethodType.methodType(ClassHeader.class))
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
                .findStatic(clazz, Constants.INJECTED_GET_ORIGINAL_FIELD_HEADERS.name(), MethodType.methodType(Set.class))
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
                .findStatic(clazz, Constants.INJECTED_GET_ORIGINAL_METHODS_HEADERS.name(), MethodType.methodType(Set.class))
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
     * Enables logging of method / constructor invocations for the given executable.
     *
     * @param executable the method / constructor to enable invocation logging for
     */
    public void enableMethodInvocationLogging(Executable executable) {
        enableMethodInvocationLogging(new MethodHeader(executable));
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
     * Returns all logged invocations for the given method / constructor.
     *
     * @param executable the method / constructor to get invocations of
     * @return a list of invocations on the given method
     */
    public List<Invocation> getInvocationsForMethod(Executable executable) {
        return getInvocationsForMethod(new MethodHeader(executable));
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
     * Substitute calls to the given method / constructor with the invocation of the given {@link MethodSubstitution}.
     * In other words, instead of executing the instructions of either the original submission or the solution,
     * this can be used to make the method do and return anything during runtime.
     *
     * @param executable the method / constructor to substitute
     * @param substitute the {@link MethodSubstitution} the method will be substituted with
     */
    public void substituteMethod(Executable executable, MethodSubstitution substitute) {
        substituteMethod(new MethodHeader(executable), substitute);
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
     * Disables delegation to the solution for the given executable.
     *
     * @param executable the method / constructor to disable delegation for
     */
    public void disableMethodDelegation(Executable executable) {
        disableMethodDelegation(new MethodHeader(executable));
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
     * Collection of methods injected into the bytecode of transformed methods.
     */
    public final class Internal {

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
