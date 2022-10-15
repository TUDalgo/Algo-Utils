package org.tudalgo.algoutils.reflect;

import net.bytebuddy.ByteBuddy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.MockingDetails;
import org.sourcegrade.jagr.api.testing.SourceFile;
import org.sourcegrade.jagr.api.testing.extension.TestCycleResolver;
import spoon.Launcher;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.*;

/**
 * A class tester which tests properties of a class.
 *
 * @author Ruben Deisenroth
 */
public class ClassTester<T> {
    /**
     * The class identifier containing the name of the class and the similarity to accept
     * alternative identifiers.
     */
    IdentifierMatcher classIdentifier;
    /**
     * The resolved class that will be tested.
     */
    Class<T> theClass;
    /**
     * The expected access modifier count.
     */
    int accessModifier;
    /**
     * The class instance of the tested class.
     */
    T classInstance;
    /**
     * The expected super class.
     */
    private Class<? super T> superClass;
    /**
     * The matcher used for the interfaces that are expected to be implemented.
     */
    private List<IdentifierMatcher> implementsInterfaces;
    /**
     * The spoon launcher which allows source code analysis and transformation.
     */
    private Launcher spoon = new Launcher();

    /**
     * Constructs and initializes a class tester.
     *
     * @param packageName          the package name of the class to be tested
     * @param className            the name of the class that should be tested
     * @param similarity           the maximum allowed name similarity to match
     * @param accessModifier       the expected access modifier
     * @param superClass           the expected super class
     * @param implementsInterfaces the matcher used for the interfaces that are expected to be
     *                             implemented
     * @param classInstance        the class instance of the tested class
     */
    public ClassTester(final String packageName, final String className, final double similarity, final int accessModifier,
                       final Class<? super T> superClass, final List<IdentifierMatcher> implementsInterfaces, final T classInstance) {
        this.classIdentifier = new IdentifierMatcher(className, packageName, similarity);
        this.accessModifier = accessModifier;
        this.superClass = superClass;
        this.implementsInterfaces = implementsInterfaces;
        this.classInstance = classInstance;
    }

    /**
     * Constructs and initializes a class tester.
     *
     * @param packageName          the package name of the class to be tested
     * @param className            the name of the class that should be tested
     * @param similarity           the maximum allowed name similarity to match
     * @param accessModifier       the expected access modifier
     * @param superClass           the expected super class
     * @param implementsInterfaces the matcher used for the interfaces that are expected to be
     *                             implemented
     */
    public ClassTester(final String packageName, final String className, final double similarity, final int accessModifier,
                       final Class<? super T> superClass, final ArrayList<IdentifierMatcher> implementsInterfaces) {
        this(packageName, className, similarity, accessModifier, superClass, implementsInterfaces, null);
    }

    /**
     * Constructs and initializes a class tester.
     *
     * @param packageName    the package name of the class to be tested
     * @param className      the name of the class that should be tested
     * @param similarity     the maximum allowed name similarity to match
     * @param accessModifier the expected access modifier
     */
    public ClassTester(final String packageName, final String className, final double similarity, final int accessModifier) {
        this(packageName, className, similarity, accessModifier, null, new ArrayList<>(), null);
    }

    /**
     * Constructs and initializes a class tester.
     *
     * @param packageName the package name of the class to be tested
     * @param className   the name of the class that should be tested
     * @param similarity  the maximum allowed name similarity to match
     */
    public ClassTester(final String packageName, final String className, final double similarity) {
        this(packageName, className, similarity, -1, null, new ArrayList<>(), null);
    }

    /**
     * Constructs and initializes a class tester.
     *
     * @param packageName the package name of the class to be tested
     * @param className   the name of the class that should be tested
     */
    public ClassTester(final String packageName, final String className) {
        this(packageName, className, 1, -1, null, new ArrayList<>(), null);
    }

    /**
     * Constructs and initializes a class tester.
     *
     * @param clazz the class to be tested
     */
    public ClassTester(final Class<T> clazz) {
        this(
            clazz.getPackageName(),
            clazz.getSimpleName(),
            0.8,
            clazz.getModifiers(),
            clazz.getSuperclass(),
            Arrays.stream(clazz.getInterfaces())
                .map(x -> new IdentifierMatcher(x.getSimpleName(), x.getPackageName(), 0.8))
                .collect(Collectors.toCollection(ArrayList::new)),
            null);
        setTheClass(clazz);
    }

    /**
     * Returns all fields of a class and its super classes recursively.
     *
     * @param fields the found fields so far (initially an empty list)
     * @param clazz  the class to look up for its fields
     * @return the found fields of a class and its super classes
     */
    private static List<Field> getAllFields(final List<Field> fields, final Class<?> clazz) {
        fields.addAll(Arrays.asList(clazz.getDeclaredFields()));

        if (clazz.getSuperclass() != null) {
            getAllFields(fields, clazz.getSuperclass());
        }

        return fields;
    }

    /**
     * Returns all fields of a class and its super classes recursively.
     *
     * @param clazz the class to look up for its fields
     * @return the found fields of a class and its super classes
     */
    public static List<Field> getAllFields(final Class<?> clazz) {
        return getAllFields(new ArrayList<>(), clazz);
    }

    /**
     * Returns all fields of the test class and its super classes.
     *
     * @return all fields of the test class and its super classes
     */
    public List<Field> getAllFields() {
        return getAllFields(new ArrayList<>(), getTheClass());
    }

    /**
     * Generates a predefined class not found message.
     *
     * @param className the class name used for the message
     * @return a predefined class not found message
     */
    public static String getClassNotFoundMessage(final String className) {
        return String.format("Klasse %s existiert nicht.", className);
    }

    /**
     * Generates a predefined class not found message.
     *
     * @return a predefined class not found message
     */
    public String getClassNotFoundMessage() {
        return getClassNotFoundMessage(this.classIdentifier.identifierName);
    }

    /**
     * Generates a predefined interface not found message.
     *
     * @param interfaceName the name of the interface used for the message
     * @return a predefined interface not found message
     */
    public static String getInterfaceNotImplementedMessage(final String interfaceName) {
        return String.format("Interface %s wird nicht erweitert.", interfaceName);
    }

    /**
     * Tests whether the class instance is not {@code null} and fails with a proper message if it is
     * {@code null}.
     *
     * @param theClass  the {@link Class}
     * @param className the Class Name for the error Message
     */
    public static void assertClassNotNull(final Class<?> theClass, final String className) {
        assertNotNull(theClass, getClassNotFoundMessage(className));
    }

    /**
     * Generates a predefined message for a missing enum constant.
     *
     * @param constantName the enum constant used for the message
     * @return a predefined message for a missing enum constant
     */
    public static String getEnumConstantMissingMessage(final String constantName) {
        return String.format("Enum-Konstante %s fehlt.", constantName);
    }

    /**
     * Returns a random enum constant from the available enum constants.
     *
     * @param enumClass     the enum instance to retrieve its enum
     * @param enumClassName the name of the enumeration class
     * @return the random enum constant
     */
    public static Enum<?> getRandomEnumConstant(final Class<Enum<?>> enumClass, final String enumClassName) {
        assertIsEnum(enumClass, enumClassName);
        final var enumConstants = enumClass.getEnumConstants();
        if (enumConstants.length == 0) {
            return null;
        }
        return enumConstants[ThreadLocalRandom.current().nextInt(enumConstants.length)];
    }

    /**
     * Returns a random enum constant.
     *
     * @return a random enum constant
     */
    @SuppressWarnings("unchecked")
    public Enum<?> getRandomEnumConstant() {
        assertIsEnum();
        return getRandomEnumConstant((Class<Enum<?>>) this.theClass, this.classIdentifier.identifierName);
    }

    /**
     * Returns the default value for the specified type.
     *
     * @param type the class type of the default type
     * @return the default value for the specified type
     */
    public static Object getDefaultValue(final Class<?> type) {
        if (type == null) {
            return null;
        } else if (type == short.class || type == Short.class) {
            return (short) 0;
        } else if (type == int.class || type == Integer.class) {
            return 0;
        } else if (type == long.class || type == Long.class) {
            return (long) 0;
        } else if (type == float.class || type == Float.class) {
            return (float) 0;
        } else if (type == double.class || type == Double.class) {
            return (double) 0;
        } else if (type == char.class || type == Character.class) {
            return 'a';
        } else if (type == boolean.class || type == Boolean.class) {
            return false;
        } else {
            return null;
        }
    }

    /**
     * Returns a random value for the specified type.
     *
     * @param type the type to generate a random value
     * @return a random value for the specified type
     */
    @SuppressWarnings("unchecked")
    public static Object getRandomValue(final Class<?> type) {
        if (type == null) {
            return null;
        }
        if (type == byte.class || type == Byte.class) {
            return (byte) ThreadLocalRandom.current().nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
        } else if (type == short.class || type == Short.class) {
            return (short) ThreadLocalRandom.current().nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
        } else if (type == int.class || type == Integer.class) {
            return ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
        } else if (type == long.class || type == Long.class) {
            return ThreadLocalRandom.current().nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
        } else if (type == float.class || type == Float.class) {
            return (float) ThreadLocalRandom.current().nextDouble(Float.MIN_VALUE, Float.MAX_VALUE);
        } else if (type == double.class || type == Double.class) {
            return ThreadLocalRandom.current().nextDouble(Double.MIN_VALUE, Double.MAX_VALUE);
        } else if (type == char.class || type == Character.class) {
            return (char) ThreadLocalRandom.current().nextInt(Character.MIN_VALUE, Character.MAX_VALUE);
        } else if (type == boolean.class) {
            return ThreadLocalRandom.current().nextBoolean();
        } else if (type.isEnum()) {
            return getRandomEnumConstant((Class<Enum<?>>) type, type.getName());
        } else {
            return findInstance(type, type.getName() + "Impl" + ThreadLocalRandom.current().nextInt(1000, 10000));
        }
    }

    /**
     * Generates a derived class from a specified class.
     *
     * @param <T>              the type of the class
     * @param clazz            the source class instance
     * @param className        the source class name
     * @param derivedClassName the name for the derived class
     * @return the generated derived class
     */
    public static <T> Class<? extends T> generateDerivedClass(final Class<T> clazz, final String className,
                                                              final String derivedClassName) {
        assertClassNotNull(clazz, className);

        return new ByteBuddy()
            .subclass(clazz)
            .make()
            .load(clazz.getClassLoader())
            .getLoaded();
    }

    /**
     * Resolves an instance of a given class (even abstract).
     *
     * @param <T>       the type of the instance to resolve
     * @param clazz     the class to generate from the instance
     * @param className the class name
     * @return the resolved instance
     */
    @SuppressWarnings("unchecked")
    public static <T> T findInstance(final Class<? super T> clazz, final String className) {
        assertClassNotNull(clazz, className);
        return (T) mock(clazz, CALLS_REAL_METHODS);
    }

    /**
     * Resolves an instance of a given class (even abstract).
     *
     * @param <T>       the type of the instance to resolve
     * @param clazz     the class to generate from the instance
     * @param className the class name
     * @return the resolved instance
     */
    @SuppressWarnings("unchecked")
    public static <T> T legacyFindInstance(Class<? super T> clazz, final String className) {
        assertClassNotNull(clazz, className);
        if (Modifier.isAbstract(clazz.getModifiers())) {
            clazz = (Class<T>) generateDerivedClass(clazz, className,
                className + ThreadLocalRandom.current().nextInt(1000, 10000));
        }
        assertFalse(Modifier.isAbstract(clazz.getModifiers()), "Kann keine Abstrakten Klasssen instanziieren.");
        final var constructors = clazz.getDeclaredConstructors();
        T instance = null;
        for (final var c : constructors) {
            try {
                c.setAccessible(true);
                final var params = c.getParameters();

                final var constructorArgs = Arrays.stream(params).map(x -> getDefaultValue(x.getType())).toArray();
                instance = (T) c.newInstance(constructorArgs);
                break;
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
        assertNotNull(instance, "Could not create Instance.");
        return instance;
    }

    /**
     * Sets the typed field to the specified value.
     *
     * @param field the field instance to change its content
     * @param obj   the object instance containing the field
     * @param value the new value of the field
     * @throws IllegalAccessException   if this {@code Field} object is enforcing Java language
     *                                  access control and the underlying field is either
     *                                  inaccessible or final; or if this {@code Field} object has
     *                                  no write access.
     * @throws IllegalArgumentException if the specified object is not an instance of the class or
     *                                  interface declaring the underlying field (or a subclass or
     *                                  implementor thereof), or if an unwrapping conversion fails.
     */
    public static void setFieldTyped(final Field field, final Object obj, final Object value)
        throws IllegalArgumentException, IllegalAccessException {
        if (field == null) {
            return;
        }
        final var type = field.getType();
        if (type == byte.class || type == Byte.class) {
            field.setByte(obj, (byte) value);
        } else if (type == short.class || type == Short.class) {
            field.setShort(obj, (short) value);
        } else if (type == int.class || type == Integer.class) {
            field.setInt(obj, (int) value);
        } else if (type == long.class || type == Long.class) {
            field.setLong(obj, (long) value);
        } else if (type == float.class || type == Float.class) {
            field.setFloat(obj, (float) value);
        } else if (type == double.class || type == Double.class) {
            field.setDouble(obj, (double) value);
        } else if (type == char.class || type == Character.class) {
            field.setChar(obj, (char) value);
        } else if (type == boolean.class || type == Boolean.class) {
            field.setBoolean(obj, (boolean) value);
        } else {
            field.set(obj, value);
        }
    }

    /**
     * Sets the field to the specified value.
     *
     * @param instance the object instance containing the field
     * @param field    the field instance to change its content
     * @param value    the new value of the field
     */
    public static void setField(final Object instance, final Field field, final Object value) {
        assertNotNull(field, "Das Feld wurde nicht gefunden.");
        assertDoesNotThrow(() -> {
            field.setAccessible(true);
            setFieldTyped(field, instance, value);
        }, "Konnte nicht auf Attribut " + field.getName() + " zugreifen.");
    }

    /**
     * Sets a field value to the specified value.
     *
     * @param field the field to modify
     * @param value the new value of the field
     */
    public void setField(final Field field, final Object value) {
        setField(getClassInstance(), field, value);
    }

    /**
     * Returns the content of a field.
     *
     * @param instance the object instance containing the field
     * @param field    the field to retrieve its content
     * @return the content of a field.
     */
    public static Object getFieldValue(final Object instance, final Field field) {
        assertNotNull(field, "Das Feld wurde nicht gefunden.");
        assertNotNull(instance, "Es wurde keine Klassen-Instanz gefunden.");
        return assertDoesNotThrow(() -> field.get(instance));
    }

    /**
     * Returns the field content.
     *
     * @param field the field to access
     * @return the field content
     */
    public Object getFieldValue(final Field field) {
        assertNotNull(field, "Das Feld wurde nicht gefunden.");
        assertclassInstanceResolved();
        if (!field.canAccess(Modifier.isStatic(field.getModifiers()) ? null : getClassInstance())) {
            assertDoesNotThrow(() -> field.setAccessible(true));
        }
        return assertDoesNotThrow(() -> field.get(getClassInstance()));
    }

    /**
     * Sets a field value to a random value.
     *
     * @param field the field to modify
     */
    public Object setFieldRandom(final Field field) {
        assertNotNull(field, "Das Feld wurde nicht gefunden.");
        final var value = getRandomValue(field.getType());
        setField(field, value);
        return value;
    }

    /**
     * Tests whether the specified field has a specified content.
     *
     * @param field             the field to check
     * @param expected          the expected content of the field
     * @param additionalMessage the additional message if the test fails
     */
    public void assertFieldEquals(final Field field, final Object expected, final String additionalMessage) {
        assertNotNull(field, "Fehlerhafter Test:Das Attribut konnte nicht gefunden werden.");
        final var message = "Das Attribut " + field.getName() + " hat den falschen Wert."
            + (additionalMessage == null ? "" : "\n" + additionalMessage);
        final var actual = getFieldValue(field);
        if (expected == null && actual != null || (expected != null && !expected.equals(actual))) {
            fail(expected == null ? null : expected.getClass().getName() + "@" + Integer.toHexString(expected.hashCode())
                + "], but got: ["
                +
                (actual == null ? null
                    : actual.getClass().getName() + "@"
                    + Integer.toHexString(actual.hashCode()))
                + "]");
        }
    }

    /**
     * Tests whether a field has a certain content.
     *
     * @param field    the field to check
     * @param expected the expected content
     */
    public void assertFieldEquals(final Field field, final Object expected) {
        assertFieldEquals(field, expected, "");
    }

    /**
     * Returns the implemented interfaces of the tested class.
     *
     * @return the implemented interfaces of the tested class
     */
    public List<IdentifierMatcher> getImplementsInterfaces() {
        return this.implementsInterfaces;
    }

    /**
     * Sets the implemented class of the tested class to the specified value.
     *
     * @param implementsInterfaces the new implemented class of the test class
     */
    public void setImplementsInterfaces(final List<IdentifierMatcher> implementsInterfaces) {
        this.implementsInterfaces = implementsInterfaces;
    }

    /**
     * Returns the super class of the tested class.
     *
     * @return the super class of the tested class
     */
    public Class<? super T> getSuperClass() {
        return this.superClass;
    }

    /**
     * Sets the super class of the tested class to the specified value.
     *
     * @param superClass the new super class of the test class
     */
    public void setSuperClass(final Class<? super T> superClass) {
        this.superClass = superClass;
    }

    /**
     * Adds an interface matcher used for the test class.
     *
     * @param interfaceMatcher the interface matcher to add
     */
    public void addImplementsInterface(final IdentifierMatcher interfaceMatcher) {
        if (this.implementsInterfaces == null) {
            this.implementsInterfaces = new ArrayList<>();
        }
        this.implementsInterfaces.add(interfaceMatcher);
    }

    /**
     * Adds an interface matcher used for the test class.
     *
     * @param interfaceName the name interface matcher to add
     * @param similarity    the maximum allowed similarity
     */
    public void addImplementsInterface(final String interfaceName, final double similarity) {
        addImplementsInterface(new IdentifierMatcher(interfaceName, similarity));
    }

    /**
     * Adds an interface matcher used for the test class.
     *
     * @param interfaceName the name interface matcher to add
     */
    public void addImplementsInterface(final String interfaceName) {
        addImplementsInterface(interfaceName, 1.0);
    }

    /**
     * Returns the spoon launcher which allows source code analysis and transformation.
     *
     * @return the spoon launcher which allows source code analysis and transformation
     */
    public Launcher getSpoon() {
        return this.spoon;
    }

    /**
     * Sets the spoon launcher which allows source code analysis and transformation to the specified
     * value.
     *
     * @param spoon the new spoon launcher
     */
    public void setSpoon(final Launcher spoon) {
        this.spoon = spoon;
    }

    /**
     * Returns instance of a class tester with the needed configuration for spoon.
     *
     * @return instance of a class tester with the needed configuration for spoon
     */
    public ClassTester<T> assureSpoonLauncherModelsBuild() {
        assureClassResolved();
        if (this.spoon == null) {
            this.spoon = new Launcher();
        }
        final var allTypes = this.spoon.getModel().getAllTypes();
        if (allTypes == null || allTypes.isEmpty()) {
            final var cycle = TestCycleResolver.getTestCycle();
            final var sourceFileName = getTheClass().getName().replace('.', '/') + ".java";
            final SourceFile sourceFile = Objects.requireNonNull(cycle).getSubmission().getSourceFile(sourceFileName);
            // fail(sourceFile.getFileName() + "\n" + sourceFile.getContent());
            this.spoon.addInputResource(
                new spoon.support.compiler.VirtualFile(Objects.requireNonNull(sourceFile).getContent(),
                    sourceFileName));
            this.spoon.buildModel();
        }
        return this;
    }

    /**
     * Resolves a field (attribute) with a specified matcher.
     *
     * @param matcher the matcher used to resolve the attribute (field)
     * @return the resolved field
     */
    public Field resolveAttribute(final AttributeMatcher matcher) {
        assertClassResolved();
        final List<Field> fields = matcher.allowSuperClass ? getAllFields(this.theClass)
            : new ArrayList<>(Arrays.asList(this.theClass.getDeclaredFields()));
        final Field bestMatch = fields.stream().min((x, y) -> Double.compare(TestUtils.similarity(y.getName(), matcher.identifierName), TestUtils.similarity(x.getName(), matcher.identifierName))).orElse(null);
        assertNotNull(bestMatch, String.format("Attribut %s existiert nicht.", matcher.identifierName));
        final var sim = TestUtils.similarity(bestMatch.getName(), matcher.identifierName);
        assertTrue(sim >= matcher.similarity,
            String.format("Attribut %s existiert nicht. Ähnlichstes Attribut: %s mit Ähnlichkeit: %s",
                matcher.identifierName, bestMatch, sim));
        if (matcher.modifier >= 0) {
            TestUtils.assertModifier(matcher.modifier, bestMatch);
        }
        return bestMatch;
    }

    /**
     * Tests whether the specified field has a Getter-Method.
     *
     * @param attribute  the field to check
     * @param parameters the parameter matcher to match the field
     */
    public void assertHasGetter(final Field attribute, final ParameterMatcher... parameters) {
        assertNotNull(attribute);

        // Method Declaration
        final var methodTester = new MethodTester(this, String.format("get%s%s",
            attribute.getName().substring(0, 1).toUpperCase(), attribute.getName().substring(1)), 0.8,
            Modifier.PUBLIC, attribute.getType(), new ArrayList<>(Arrays.asList(parameters)));
        methodTester.resolveMethod();
        methodTester.assertAccessModifier();
        methodTester.assertParametersMatch();
        methodTester.assertReturnType();

        // test with Value

        assertDoesNotThrow(() -> attribute.setAccessible(true),
            "Konnte nicht auf Attribut zugreifen:" + attribute.getName());

        resolveInstance();

        final var expectedReturnValue = getRandomValue(attribute.getType());
        assertDoesNotThrow(() -> attribute.set(getClassInstance(), expectedReturnValue));
        final var returnValue = methodTester
            .invoke(Arrays.stream(parameters).map(x -> getRandomValue(x.parameterType)).toArray());
        assertEquals(expectedReturnValue, returnValue, "Falsche Rückgabe der Getter-Methode.");
    }

    /**
     * Tests whether the specified field has a Setter-Method.
     *
     * @param attribute the field to check
     * @param testValue the test value to test the Setter-Method
     */
    public void assertHasSetter(final Field attribute, final Object testValue) {
        assertNotNull(attribute);

        // Method Declaration
        final var methodTester = new MethodTester(this, String.format("set%s%s",
            attribute.getName().substring(0, 1).toUpperCase(), attribute.getName().substring(1)), 0.8,
            Modifier.PUBLIC, void.class,
            List.of(new ParameterMatcher(attribute.getName(), 0.8, attribute.getType()))).verify();

        // test with Value
        methodTester.invoke(testValue);
        assertFieldEquals(attribute, testValue, "Falscher Wert durch Setter-Methode.");
    }

    /**
     * Tests whether the specified field has a Setter-Method.
     *
     * @param attribute the field to check
     */
    public void assertHasSetter(final Field attribute) {
        assertNotNull(attribute);
        assertHasSetter(attribute, getRandomValue(attribute.getType()));
    }

    /**
     * Tests whether all described interfaces by the specified matchers are being extended.
     *
     * @param implementsInterfaces the matchers to match the criterion of extension
     */
    public void assertImplementsInterfaces(final List<IdentifierMatcher> implementsInterfaces) {
        assertClassResolved();
        final var interfaces = new ArrayList<>(List.of(this.theClass.getInterfaces()));
        if (implementsInterfaces == null || implementsInterfaces.isEmpty()) {
            assertTrue(interfaces.isEmpty(), "Es sollen keine Interfaces implementiert werden.");
        } else {
            for (final IdentifierMatcher matcher : implementsInterfaces) {
                assertFalse(interfaces.isEmpty(), getInterfaceNotImplementedMessage(matcher.identifierName));
                final var bestMatch = interfaces.stream()
                    .min((x, y) -> Double.compare(TestUtils.similarity(matcher.identifierName, y.getSimpleName()),
                        TestUtils.similarity(matcher.identifierName, x.getSimpleName())))
                    .orElse(null);
                assertNotNull(bestMatch, getInterfaceNotImplementedMessage(matcher.identifierName));
                final var sim = TestUtils.similarity(bestMatch.getSimpleName(), matcher.identifierName);
                assertTrue(sim >= matcher.similarity, getInterfaceNotImplementedMessage(matcher.identifierName)
                    + "Ähnlichstes Interface:" + bestMatch.getSimpleName() + " with " + sim + " similarity.");
                interfaces.remove(bestMatch);
            }
            assertTrue(interfaces.isEmpty(),
                "Die folgenden Interfaces sollten nicht implementiert werden:" + interfaces);
        }
    }

    /**
     * Tests whether all described interfaces by the specified matchers are being extended.
     */
    public void assertImplementsInterfaces() {
        assertImplementsInterfaces(this.implementsInterfaces);
    }

    /**
     * Tests whether there are no interface extension.
     */
    public void assertDoesNotImplementAnyInterfaces() {
        assertImplementsInterfaces(null);
    }

    /**
     * Returns {@code true} if {@link #theClass} is not null.
     *
     * @return {@code true} if {@link #theClass} is not null
     */
    public boolean class_resolved() {
        return this.theClass != null;
    }

    /**
     * Returns the {@link MockingDetails} of {@link #theClass}.
     *
     * @return the {@link MockingDetails} of {@link #theClass}
     */
    public MockingDetails getMockingDetails() {
        return mockingDetails(getClassInstance());
    }

    /**
     * Returns {@code true} if {@link #theClass} is mocked.
     *
     * @return {@code true} if {@link #theClass} is mocked
     * @see MockingDetails#isMock()
     */
    public boolean is_mock() {
        return classInstanceResolved() && mockingDetails(getClassInstance()).isMock();
    }

    /**
     * Returns {@code true} if {@link #theClass} is a spy.
     *
     * @return {@code true} if {@link #theClass} is a spy
     * @see MockingDetails#isSpy()
     */
    public boolean is_spy() {
        return classInstanceResolved() && mockingDetails(getClassInstance()).isSpy();
    }

    /**
     * Makes the class a spy if not done already.
     *
     * @return this class tester
     */
    public ClassTester<T> assureSpied() {
        assertclassInstanceResolved();
        if (!is_spy()) {
            setClassInstance(spy(getClassInstance()));
        }
        return this;
    }

    /**
     * Tests whether this class is a spy.
     *
     * @return this class tester
     */
    public ClassTester<T> assertSpied() {
        assertclassInstanceResolved();
        assertTrue(is_spy(), "Faulty Test: Class was not spied on");
        return this;
    }

    /**
     * Tests that {@link #theClass} is not {@code null} and fails with the predefined message if it
     * cannot be resolved.
     */
    public void assertClassResolved() {
        assertClassNotNull(this.theClass, this.classIdentifier.identifierName);
    }

    /**
     * Tests whether the Class is declared correctly.
     *
     * @return this class tester
     */
    public ClassTester<T> verify() {
        if (!class_resolved()) {
            resolveClass();
        }
        if (this.accessModifier >= 0) {
            // Class Type
            if (Modifier.isInterface(getAccessModifier())) {
                assertIsInterface();
            } else if ((getAccessModifier() & TestUtils.ENUM) != 0) {
                assertIsEnum();
            } else {
                assertIsPlainClass();
            }
            assertAccessModifier();
        }
        assertSuperclass();
        assertImplementsInterfaces();
        return this;
    }

    /**
     * Tests whether the Class is declared correctly.
     *
     * @param minSimilarity the minimum required similarity
     * @return this class tester
     */
    public ClassTester<T> verify(final double minSimilarity) {
        final var currSim = getClassIdentifier().similarity;
        getClassIdentifier().similarity = minSimilarity;
        verify();
        getClassIdentifier().similarity = currSim;
        return this;
    }

    /**
     * Tests whether the super classes fo the test classes matches the specified super classes.
     */
    public void assertSuperclass() {
        assertClassResolved();

        if (this.superClass == null) {
            if (getAccessModifier() >= 0) {
                if ((getAccessModifier() & TestUtils.ENUM) != 0) {
                    assertSame(Enum.class, this.theClass.getSuperclass());
                } else if (Modifier.isInterface(getAccessModifier())) {
                    assertSame(null, this.theClass.getSuperclass());
                } else {
                    assertSame(Object.class, this.theClass.getSuperclass());
                }
            }
        } else {
            assertSame(this.superClass, this.theClass.getSuperclass());
        }
    }

    /**
     * Returns the test classes if it is already resolved.
     *
     * @return the test classes if it is already resolved
     */
    public Class<T> getTheClass() {
        return this.theClass;
    }

    /**
     * Sets the test class instance to the specified class instance.
     *
     * @param theClass the new test class instance
     */
    public void setTheClass(final Class<T> theClass) {
        this.theClass = theClass;
    }

    /**
     * Returns the expected access modifier.
     *
     * @return the expected access modifier
     */
    public int getAccessModifier() {
        return this.accessModifier;
    }

    /**
     * Sets the expected access modifier the specified value.
     *
     * @param accessModifier the new expected access modifier count
     */
    public void setAccessModifier(final int accessModifier) {
        this.accessModifier = accessModifier;
    }

    /**
     * Tests whether the access modifier count is correct and fails it with a predefined message if
     * it fails.
     */
    public void assertAccessModifier() {
        if (this.accessModifier >= 0) {
            TestUtils.assertModifier(this.accessModifier, this.theClass);
        }
    }

    /**
     * Returns class instance of the tested class.
     *
     * @return class instance of the tested class
     */
    public T getClassInstance() {
        return this.classInstance;
    }

    /**
     * Sets class instance of the tested class to the specified value.
     *
     * @param classInstance the new value of the class instance of the tested class
     */
    public void setClassInstance(final T classInstance) {
        this.classInstance = classInstance;
    }

    /**
     * Returns {@code true} if the class instance of the tested class is not {@code null}.
     *
     * @return {@code true} if the class instance of the tested class is not {@code null}
     */
    public boolean classInstanceResolved() {
        return this.classInstance != null;
    }

    /**
     * Tests whether the class instance of the tested class is not {@code null}.
     */
    public void assertclassInstanceResolved() {
        assertNotNull(this.classInstance, "Es wurde keine Klassen-Instanz gefunden.");
    }

    /**
     * Tests whether the enum constants with the specified names exists.
     *
     * @param expectedConstants the name of the enum constants to check
     */
    public void assertEnumConstants(final String[] expectedConstants) {
        assertClassResolved();
        final var enumValues = this.theClass.getEnumConstants();
        for (final String n : expectedConstants) {
            assertTrue(Stream.of(enumValues).anyMatch(x -> x.toString().equals(n)),
                String.format("Enum-Konstante %s fehlt.", n));
        }
    }

    /**
     * Returns the class matcher used on the test class.
     *
     * @return the class matcher used on the test class
     */
    public IdentifierMatcher getClassIdentifier() {
        return this.classIdentifier;
    }

    /**
     * Sets the class matcher used on the test class to the new value.
     *
     * @param classIdentifier the new the class matcher used on the test class
     */
    public void setClassIdentifier(final IdentifierMatcher classIdentifier) {
        this.classIdentifier = classIdentifier;
    }

    /**
     * Resolves a class with the specified name and similarity.
     *
     * @param packageName the package name of the class
     * @param className   the name of the class to resolve
     * @param similarity  The minimum required similarity
     * @return the resolved Class With the given name and similarity
     */
    @SuppressWarnings("unchecked")
    public Class<T> findClass(final String packageName, final String className, final double similarity) {
        // if (similarity >= 1) {
        // return theClass = (Class<T>) assertDoesNotThrow(
        // () -> Class.forName(String.format("%s.%s", packageName, className)),
        // getClassNotFoundMessage(className));
        // }
        final var classes = assertDoesNotThrow(() -> TestUtils.getClasses(packageName));
        final var bestMatch = Arrays.stream(classes)
            .min((x, y) -> Double.compare(TestUtils.similarity(className, y.getSimpleName()),
                TestUtils.similarity(className, x.getSimpleName())))
            .orElse(null);
        assertNotNull(bestMatch, getClassNotFoundMessage());
        final var sim = TestUtils.similarity(bestMatch.getSimpleName(), className);
        assertTrue(sim >= similarity, getClassNotFoundMessage() + "Ähnlichster Klassenname:" + bestMatch.getSimpleName()
            + " with " + sim + " similarity.");
        return this.theClass = (Class<T>) bestMatch;
    }

    /**
     * Resolves a class with the current class name and similarity.
     *
     * @return the resolved class with the current class name and similarity.
     */
    public Class<T> findClass() {
        return findClass(this.classIdentifier.packageName, this.classIdentifier.identifierName, this.classIdentifier.similarity);
    }

    /**
     * Resolves a class with the specified similarity.
     *
     * @param similarity the minimum required similarity
     * @return the resolved class  with the specified similarity
     */
    public Class<T> findClass(final double similarity) {
        return findClass(this.classIdentifier.packageName, this.classIdentifier.identifierName, similarity);
    }

    /**
     * Finds the class to test and stores it.
     *
     * @return this class tester
     */
    public ClassTester<T> resolveClass() {
        this.theClass = findClass();
        return this;
    }

    /**
     * Resolves the class if necessary. (We do not care about fields being made accessible here)
     *
     * @return this class tester
     */
    public ClassTester<T> assureClassResolved() {
        if (!class_resolved()) {
            resolveClass();
        }
        return this;
    }

    /**
     * Resolves the class and instance and stores them.
     *
     * @return this class tester
     */
    public ClassTester<T> resolve() {
        assureClassResolved();
        resolveInstance();
        return this;
    }

    /**
     * Resolves the class and instance and stores them.
     *
     * @return this class tester
     */
    public ClassTester<T> resolveReal() {
        assureClassResolved();
        resolveRealInstance();
        return this;
    }

    /**
     * Resolves a test class instance. (even abstract)
     *
     * @return the test class instance
     */
    public T resolveInstance() {
        return this.classInstance = findInstance(this.theClass, this.classIdentifier.identifierName);
    }

    /**
     * Returns a new test class instance.
     *
     * @return the new test class instance
     */
    public T getNewInstance() {
        return findInstance(this.theClass, this.classIdentifier.identifierName);
    }

    /**
     * Returns a new test class instance.
     *
     * @return the new test class instance
     */
    public T getNewRealInstance() {
        return legacyFindInstance(this.theClass, this.classIdentifier.identifierName);
    }

    /**
     * Resolve a real test class instance.
     *
     * @return this class tester
     */
    public ClassTester<T> resolveRealInstance() {
        setClassInstance(legacyFindInstance(this.theClass, this.classIdentifier.identifierName));
        return this;
    }

    /**
     * Resolves a constructor with the specified parameters.
     *
     * @param parameters the parameters of the constructor
     * @return the best matched constructor
     */
    @SuppressWarnings("unchecked")
    public Constructor<T> resolveConstructor(final List<ParameterMatcher> parameters) {
        assertClassResolved();
        final Constructor<T>[] constructors = (Constructor<T>[]) assertDoesNotThrow(() -> this.theClass.getDeclaredConstructors());
        assertTrue(constructors.length > 0, "Keine Konstruktoren gefunden.");
        final Constructor<T> bestMatch;
        if (parameters != null && !parameters.isEmpty()) {
            // Find best match according to parameter options
            bestMatch = Arrays.stream(constructors).min(
                Comparator.comparingInt(x -> MethodTester.countMatchingParameters(
                    parameters,
                    Arrays.asList(x.getParameters()), true
                ))
            ).orElse(null);
        } else {
            bestMatch = Arrays.stream(constructors).filter(x -> x.getParameterCount() == 0).findFirst().orElse(null);
        }
        assertNotNull(bestMatch, "Der Passende Konstruktor wurde nicht gefunden");
        return bestMatch;
    }

    /**
     * Resolves a constructor with the parameters.
     *
     * @param parameters the parameters of the constructor
     * @return the best matched constructor
     */
    public Constructor<T> resolveConstructor(final ParameterMatcher... parameters) {
        return resolveConstructor(new ArrayList<>(Arrays.asList(parameters)));
    }

    /**
     * Tests whether a constructor was declared correctly.
     *
     * @param constructor    the constructor to check
     * @param accessModifier the expected access modifier count
     * @param parameters     the expected parameters of the constructor
     */
    public void assertConstructorValid(final Constructor<T> constructor, final int accessModifier,
                                       final ArrayList<ParameterMatcher> parameters) {
        assertNotNull(constructor, "Der Passende Konstruktor wurde nicht gefunden");
        TestUtils.assertModifier(accessModifier, constructor);
        MethodTester.assertParametersMatch(parameters, new ArrayList<>(Arrays.asList(constructor.getParameters())),
            true);
    }

    /**
     * Tests whether a constructor was declared correctly.
     *
     * @param constructor    the constructor to check
     * @param accessModifier the expected access modifier count
     * @param parameters     the expected parameters of the constructor
     */
    public void assertConstructorValid(final Constructor<T> constructor, final int accessModifier,
                                       final ParameterMatcher... parameters) {
        assertConstructorValid(constructor, accessModifier, new ArrayList<>(Arrays.asList(parameters)));
    }

    /**
     * Returns the specified enum value.
     *
     * @param <T>          the type of the enum class
     * @param enumClass    the enum class
     * @param expectedName the name of the enum class
     * @param similarity   the min similarity of the name
     * @return the specified enum value
     */
    public static <T> Enum<?> getEnumValue(final Class<Enum<?>> enumClass, final String expectedName, final double similarity) {
        final var enumConstants = enumClass.getEnumConstants();
        final var bestMatch = Arrays.stream(enumConstants)
            .min((x, y) -> Double.compare(TestUtils.similarity(expectedName, y.name()),
                TestUtils.similarity(expectedName, x.name())))
            .orElse(null);
        assertNotNull(bestMatch, "Enum-Wert" + expectedName + " existiert nicht.");
        final var sim = TestUtils.similarity(expectedName, bestMatch.name());
        assertTrue(sim >= similarity,
            "Enum-Wert" + expectedName + " existiert nicht. Ähnliche Konstante:" + bestMatch.name());
        return bestMatch;
    }

    /**
     * Returns a specific enum value.
     *
     * @param expectedName the enum class name to retrieve the value.
     * @param similarity   the min similarity
     * @return the specific enum value
     */
    @SuppressWarnings("unchecked")
    public Enum<?> getEnumValue(final String expectedName, final double similarity) {
        return getEnumValue((Class<Enum<?>>) this.theClass, expectedName, similarity);
    }

    /**
     * Tests whether the class instance is an interface.
     *
     * @param theClass  the class instance to check
     * @param className the name of the class
     */
    public static void assertIsInterface(final Class<?> theClass, final String className) {
        assertClassNotNull(theClass, className);
        assertTrue(theClass.isInterface(), String.format("%s ist kein Interface.", className));
    }

    /**
     * Tests whether the test class is an interface.
     */
    public void assertIsInterface() {
        assertIsInterface(this.theClass, this.classIdentifier.identifierName);
    }

    /**
     * Tests whether the class instance is an enum.
     *
     * @param theClass  the class instance to check
     * @param className the name of the class
     */
    public static void assertIsEnum(final Class<?> theClass, final String className) {
        assertClassNotNull(theClass, className);
        assertTrue(theClass.isEnum(), String.format("%s ist kein Enum.", className));
    }

    /**
     * Tests whether the test class is an enum.
     */
    public void assertIsEnum() {
        assertIsEnum(this.theClass, this.classIdentifier.identifierName);
    }

    /**
     * Tests whether the test class is a plain class.
     */
    public void assertIsPlainClass() {
        assertIsPlainClass(this.theClass, this.classIdentifier.identifierName);
    }

    /**
     * Tests whether the class instance is a plain class.
     *
     * @param theClass  the class instance to check
     * @param className the name of the class
     */
    public static void assertIsPlainClass(final Class<?> theClass, final String className) {
        assertClassNotNull(theClass, className);
        assertFalse(theClass.isInterface(), String.format("%s sollte kein Interface sein.", className));
        assertFalse(theClass.isEnum(), String.format("%s sollte kein Enum sein.", className));
    }
}
