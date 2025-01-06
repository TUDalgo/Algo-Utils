package org.tudalgo.algoutils.tutor.general.stringify.basic;

import com.google.common.collect.Streams;
import org.tudalgo.algoutils.tutor.general.SpoonUtils;
import org.tudalgo.algoutils.tutor.general.match.BasicReflectionMatchers;
import org.tudalgo.algoutils.tutor.general.match.Matcher;
import org.tudalgo.algoutils.tutor.general.reflections.BasicTypeLink;
import org.tudalgo.algoutils.tutor.general.reflections.Link;
import org.tudalgo.algoutils.tutor.general.reflections.TypeLink;
import org.tudalgo.algoutils.tutor.general.stringify.Stringifier;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtNamedElement;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;

/**
 * A stringifier for transforming reflection object to string objects.
 */
public final class ReflectionStringifier implements Stringifier {

    /**
     * The singleton instance of this class.
     */
    private static final ReflectionStringifier instance = new ReflectionStringifier();

    /**
     * Private constructor to prevent instantiation.
     */
    private ReflectionStringifier() {
        registerDefaultTypehandlers();
    }

    /**
     * The object handlers. They are processed in the opposite order they were added. This means by adding a handler for
     * an existing matcher, the new handler will be processed first thus overriding the old handler.
     */
    private final Map<Matcher<Object>, Function<Object, String>> objectHandlers = new LinkedHashMap<>();

    /**
     * Registers an object handler for the given matcher.
     *
     * @param matcher the matcher to register the handler for
     * @param handler the handler to register
     */
    public void registerObjectHandler(Matcher<Object> matcher, Function<Object, String> handler) {
        objectHandlers.put(matcher, handler);
    }

    /**
     * Registers a type handler for the given matcher.
     *
     * @param matcher the matcher to register the handler for
     * @param handler the handler to register
     */
    public void registerTypeHandler(Matcher<TypeLink> matcher, Function<Object, String> handler) {
        objectHandlers.put(Matcher.of(e -> e.getClass().isAssignableFrom(TypeLink.class)), handler);
    }

    /**
     * Registers a type handler for the given class.
     *
     * @param clazz   the class to register the handler for
     * @param handler the handler to register
     */
    public void registerTypeHandler(Class<?> clazz, Function<Object, String> handler) {
        registerTypeHandler(BasicReflectionMatchers.sameType(clazz), handler);
    }

    /**
     * Returns an instance of this class.
     *
     * @return the instance of this class
     */
    public static ReflectionStringifier getInstance() {
        return instance;
    }

    @Override
    public String stringifyOrElseNull(Object object) {
        // iterate over all type handlers in reverse order
        for (var entry : objectHandlers.entrySet()) {
            var matcher = entry.getKey();
            var handler = entry.getValue();
            if (matcher.match(object).matched()) {
                return handler.apply(object);
            }
        }
        return Objects.toString(object);
    }

    private void registerDefaultTypehandlers() {
        registerObjectHandler(Matcher.of(Objects::isNull), e -> null);
        registerTypeHandler(
            String.class, e -> (String) e
        );
        // register type handlers for arrays
        registerObjectHandler(
            Matcher.of(e -> e.getClass().isArray(), "Array"),
            e -> getArrayStream(e)
                .map(this::stringifyOrElseNull)
                .toList()
                .toString()
        );
        // register type handlers for Iterables
        registerObjectHandler(
            Matcher.of(e -> e instanceof Iterable<?>, "Iterable"),
            e -> StreamSupport.stream(((Iterable<?>) e).spliterator(), false)
                .map(this::stringifyOrElseNull)
                .toList()
                .toString()
        );
        // register type handlers for Internal algoutils.tutor types
        registerTypeHandler(
            Matcher.class, e -> stringifyOrElseNull(((Matcher<?>) e).characteristic())
        );
        registerTypeHandler(
            Link.class, e -> stringifyOrElseNull(((Link) e).reflection())
        );
        registerTypeHandler(
            Class.class, e -> stringifyOrElseNull(((Class<?>) e).getSimpleName())
        );
        registerObjectHandler(
            Matcher.of(e -> e instanceof Class<?> clazz && clazz.isAssignableFrom(CtElement.class)),
            e -> {
                var spoonName = SpoonUtils.getNameOfCtElement((Class<?>) e);
                return spoonName != null ? spoonName : ((Class<?>) e).getSimpleName();
            }
        );
        registerTypeHandler(
            Field.class, e -> {
                var field = (Field) e;
                var clazzString = field.getDeclaringClass().getSimpleName();
                var typeString = field.getType().getSimpleName();
                var name = field.getName();
                return format("%s#%s %s", clazzString, typeString, name);
            }
        );
        registerTypeHandler(
            Method.class, e -> {
                var method = (Method) e;
                var clazzString = method.getDeclaringClass().getSimpleName();
                var name = method.getName();
                var parametersString = Stream.of(method.getParameters())
                    .map(this::stringifyOrElseNull)
                    .collect(Collectors.joining(", "));
                return format("%s#%s(%s)", clazzString, name, parametersString);
            }
        );
        registerTypeHandler(
            Parameter.class, e -> {
                var parameter = (Parameter) e;
                var typeString = parameter.getType().getSimpleName();
                return format("%s", typeString);
            }
        );
        registerTypeHandler(
            Constructor.class, e -> {
                var constructor = (Constructor<?>) e;
                return format("constructor of %s", constructor.getDeclaringClass().getSimpleName());
            }
        );
        registerTypeHandler(
            CtElement.class, e -> {
                var ctElement = (CtElement) e;
                return SpoonUtils.getNameOfCtElement(ctElement);
            }
        );
        registerTypeHandler(
            CtNamedElement.class, e -> ((CtNamedElement) e).getSimpleName()
        );
    }

    /**
     * Converts an Object of Type array to its corresponding Stream. Assumes the input to actually be an array.
     * @param e the object to turn into a stream
     * @return the corresponding stream
     */
    private Stream<?> getArrayStream(Object e) {
        Class<?> componentType = e.getClass().getComponentType();
        if(!componentType.isPrimitive()) {
            return Arrays.stream((Object[]) e);
        }
        // Streams only support int, long and double everything else would need to be cast.
        if(componentType == int.class) {
            return Arrays.stream((int[])e).boxed();
        } else if(componentType == long.class) {
            return Arrays.stream((long[]) e).boxed();
        } else if(componentType == double.class) {
            return Arrays.stream((double[]) e).boxed();
        }

        throw new IllegalArgumentException(
            "Can only cast Arrays of primitive types int, long and double. Got: " + componentType);
    }
}
