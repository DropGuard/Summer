package com.github.dropguard.summer.engine;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.UnsupportedInjectionException;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

/**
 * Jandex type-name helpers for injection-parameter collection.
 *
 * <p>Shared by constructor-parameter collection ({@link BeanEnrichment}) and factory-method
 * parameter collection ({@link Discovery}) so both engines agree on {@code List<T>} handling —
 * previously each duplicated the conversion with subtly different behavior.
 */
@Internal
public final class JandexTypes {

    private JandexTypes() {}

    /**
     * Converts a Jandex parameter type to its injection-parameter type name.
     *
     * <p>{@code java.util.List<E>} collapses to {@code "java.util.List<E>"} with the element type
     * resolved from the Jandex type. Nested generics ({@code List<List<X>>}) are rejected — the
     * framework cannot inject them. Non-list types pass through as their name.
     *
     * @param paramType the Jandex parameter type
     * @param owner description of the declaring member, for the nested-generic error message
     * @return the type name used as the bean's injection parameter
     */
    public static String paramTypeName(Type paramType, String owner) {
        if (paramType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType pt = paramType.asParameterizedType();
            if (pt.name().toString().equals("java.util.List") && pt.arguments().size() == 1) {
                Type elementTypeObj = pt.arguments().get(0);
                if (elementTypeObj.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                    throw new UnsupportedInjectionException(
                            "Nested generic type injection is not supported: List<"
                                    + elementTypeObj
                                    + "> in "
                                    + owner);
                }
                return "java.util.List<" + elementTypeObj.name() + ">";
            }
        }
        return paramType.name().toString();
    }
}
