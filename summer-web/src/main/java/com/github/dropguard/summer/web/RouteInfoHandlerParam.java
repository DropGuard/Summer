package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;
import java.lang.annotation.Annotation;

/** Reflection-free {@link HandlerParam} built from AOT-discovered route metadata. */
@Internal
public final class RouteInfoHandlerParam implements HandlerParam {

    private final Class<?> type;
    private final String bindingName;
    private final Class<? extends Annotation> annotationType;
    private final boolean validated;

    public RouteInfoHandlerParam(
            Class<?> type,
            String bindingName,
            Class<? extends Annotation> annotationType,
            boolean validated) {
        this.type = type;
        this.bindingName = bindingName;
        this.annotationType = annotationType;
        this.validated = validated;
    }

    @Override
    public Class<?> type() {
        return type;
    }

    @Override
    public String bindingName() {
        return bindingName;
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> a) {
        return annotationType != null && annotationType.equals(a);
    }

    @Override
    public boolean validated() {
        return validated;
    }
}
