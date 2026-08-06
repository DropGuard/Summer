package com.github.dropguard.summer.web;

import java.lang.annotation.Annotation;

/** Reflection-free description of a handler method parameter. */
public interface HandlerParam {

    /** The parameter's declared type. */
    Class<?> type();

    /**
     * The binding name — the {@code @PathParam}/{@code @QueryParam} value, or the parameter name
     * when no explicit value is given.
     */
    String bindingName();

    /** Whether the parameter carries the given annotation. */
    boolean hasAnnotation(Class<? extends Annotation> annotationType);

    /** Whether the parameter is annotated with {@code @Valid}. */
    boolean validated();
}
