package org.tudalgo.algoutils.transform;

import org.tudalgo.algoutils.transform.classes.SubmissionClassVisitor;
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
 * This can be done by calling {@link Delegation#disable}.
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

    // declaring class => (method header => invocations)
    private static final Map<String, Map<MethodHeader, List<Invocation>>> METHOD_INVOCATIONS = new HashMap<>();
    private static final Map<String, Map<MethodHeader, MethodSubstitution>> METHOD_SUBSTITUTIONS = new HashMap<>();
    private static final Map<String, Set<MethodHeader>> METHOD_DELEGATION_EXCLUSIONS = new HashMap<>();

    private SubmissionExecutionHandler() {}

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
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
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
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
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
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Resets all mechanisms.
     */
    public static void resetAll() {
        Logging.reset();
        Substitution.reset();
        Delegation.reset();
    }

    public static final class Logging {

        private Logging() {}

        /**
         * Enables logging of method / constructor invocations for the given executable.
         *
         * @param executable the method / constructor to enable invocation logging for
         */
        public static void enable(Executable executable) {
            enable(new MethodHeader(executable));
        }

        /**
         * Enables logging of method invocations for the given method.
         *
         * @param methodHeader a method header describing the method
         */
        public static void enable(MethodHeader methodHeader) {
            METHOD_INVOCATIONS.computeIfAbsent(methodHeader.owner(), k -> new HashMap<>())
                .putIfAbsent(methodHeader, new ArrayList<>());
        }

        /**
         * Disables logging of method / constructor invocations for the given executable.
         * Note: This also discards all logged invocations.
         *
         * @param executable the method / constructor to disable invocation logging for
         */
        public static void disable(Executable executable) {
            disable(new MethodHeader(executable));
        }

        /**
         * Disables logging of method invocations for the given method.
         * Note: This also discards all logged invocations.
         *
         * @param methodHeader a method header describing the method
         */
        public static void disable(MethodHeader methodHeader) {
            Optional.ofNullable(METHOD_INVOCATIONS.get(methodHeader.owner()))
                .ifPresent(map -> map.remove(methodHeader));
        }

        /**
         * Resets the logging of method invocations to log no invocations.
         */
        public static void reset() {
            METHOD_INVOCATIONS.clear();
        }

        /**
         * Returns all logged invocations for the given method / constructor.
         *
         * @param executable the method / constructor to get invocations of
         * @return a list of invocations on the given method
         */
        public static List<Invocation> getInvocations(Executable executable) {
            return getInvocations(new MethodHeader(executable));
        }

        /**
         * Returns all logged invocations for the given method.
         *
         * @param methodHeader a method header describing the method
         * @return a list of invocations on the given method
         */
        public static List<Invocation> getInvocations(MethodHeader methodHeader) {
            return Optional.ofNullable(METHOD_INVOCATIONS.get(methodHeader.owner()))
                .map(map -> map.get(methodHeader))
                .map(Collections::unmodifiableList)
                .orElse(null);
        }
    }

    public static final class Substitution {

        private Substitution() {}

        /**
         * Substitute calls to the given method / constructor with the invocation of the given {@link MethodSubstitution}.
         * In other words, instead of executing the instructions of either the original submission or the solution,
         * this can be used to make the method do and return anything at runtime.
         *
         * @param executable the method / constructor to substitute
         * @param substitute the {@link MethodSubstitution} the method will be substituted with
         */
        public static void enable(Executable executable, MethodSubstitution substitute) {
            enable(new MethodHeader(executable), substitute);
        }

        /**
         * Substitute calls to the given method with the invocation of the given {@link MethodSubstitution}.
         * In other words, instead of executing the instructions of either the original submission or the solution,
         * this can be used to make the method do and return anything at runtime.
         *
         * @param methodHeader a method header describing the method
         * @param substitute   the {@link MethodSubstitution} the method will be substituted with
         */
        public static void enable(MethodHeader methodHeader, MethodSubstitution substitute) {
            METHOD_SUBSTITUTIONS.computeIfAbsent(methodHeader.owner(), k -> new HashMap<>())
                .put(methodHeader, substitute);
        }

        /**
         * Disables substitution for the given method / constructor.
         *
         * @param executable the substituted method / constructor
         */
        public static void disable(Executable executable) {
            disable(new MethodHeader(executable));
        }

        /**
         * Disables substitution for the given method.
         *
         * @param methodHeader a method header describing the method
         */
        public static void disable(MethodHeader methodHeader) {
            Optional.ofNullable(METHOD_SUBSTITUTIONS.get(methodHeader.owner()))
                .ifPresent(map -> map.remove(methodHeader));
        }

        /**
         * Resets the substitution of methods.
         */
        public static void reset() {
            METHOD_SUBSTITUTIONS.clear();
        }
    }

    public static final class Delegation {

        private Delegation() {}

        /**
         * Enables delegation to the solution for the given executable.
         * Note: Delegation is enabled by default, so this method usually does not have to be called before invocations.
         *
         * @param executable the method / constructor to enable delegation for.
         */
        public static void enable(Executable executable) {
            enable(new MethodHeader(executable));
        }

        /**
         * Enables delegation to the solution for the given method.
         * Note: Delegation is enabled by default, so this method usually does not have to be called before invocations.
         *
         * @param methodHeader a method header describing the method
         */
        public static void enable(MethodHeader methodHeader) {
            Optional.ofNullable(METHOD_DELEGATION_EXCLUSIONS.get(methodHeader.owner()))
                .ifPresent(set -> set.remove(methodHeader));
        }

        /**
         * Disables delegation to the solution for the given executable.
         *
         * @param executable the method / constructor to disable delegation for
         */
        public static void disable(Executable executable) {
            disable(new MethodHeader(executable));
        }

        /**
         * Disables delegation to the solution for the given method.
         *
         * @param methodHeader a method header describing the method
         */
        public static void disable(MethodHeader methodHeader) {
            METHOD_DELEGATION_EXCLUSIONS.computeIfAbsent(methodHeader.owner(), k -> new HashSet<>()).add(methodHeader);
        }

        /**
         * Resets the delegation of methods.
         */
        public static void reset() {
            METHOD_DELEGATION_EXCLUSIONS.clear();
        }
    }

    /**
     * Collection of methods injected into the bytecode of transformed methods.
     */
    public static final class Internal {

        private Internal() {}

        // Invocation logging

        /**
         * Returns whether the calling method's invocation is logged, i.e.
         * {@link #addInvocation(MethodHeader, Invocation)} may be called or not.
         * Should only be used in bytecode transformations when intercepting method invocations.
         *
         * @param methodHeader a method header describing the method
         * @return {@code true} if invocation logging is enabled for the given method, otherwise {@code false}
         */
        public static boolean logInvocation(MethodHeader methodHeader) {
            return Optional.ofNullable(METHOD_INVOCATIONS.get(methodHeader.owner()))
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
        public static void addInvocation(MethodHeader methodHeader, Invocation invocation) {
            Optional.ofNullable(METHOD_INVOCATIONS.get(methodHeader.owner()))
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
        public static boolean useSubstitution(MethodHeader methodHeader) {
            return Optional.ofNullable(METHOD_SUBSTITUTIONS.get(methodHeader.owner()))
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
        public static MethodSubstitution getSubstitution(MethodHeader methodHeader) {
            return Optional.ofNullable(METHOD_SUBSTITUTIONS.get(methodHeader.owner()))
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
        public static boolean useSubmissionImpl(MethodHeader methodHeader) {
            return Optional.ofNullable(METHOD_DELEGATION_EXCLUSIONS.get(methodHeader.owner()))
                .map(set -> set.contains(methodHeader))
                .orElse(false);
        }
    }
}
