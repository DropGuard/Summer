package com.github.dropguard.summer.runtime.web;

import com.github.dropguard.summer.web.HandlerParam;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.QueryParam;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

/** Reflection-based {@link HandlerParam} adapter for the runtime engine. */
final class RuntimeHandlerParam implements HandlerParam {

    private final Class<?> type;
    private final String bindingName;
    private final Class<? extends Annotation> annotationType;
    private final boolean validated;

    RuntimeHandlerParam(Parameter parameter) {
        this.type = parameter.getType();
        this.validated = parameter.isAnnotationPresent(jakarta.validation.Valid.class);
        if (parameter.isAnnotationPresent(PathParam.class)) {
            PathParam ann = parameter.getAnnotation(PathParam.class);
            this.annotationType = PathParam.class;
            this.bindingName = ann.value().isEmpty() ? parameter.getName() : ann.value();
        } else if (parameter.isAnnotationPresent(QueryParam.class)) {
            QueryParam ann = parameter.getAnnotation(QueryParam.class);
            this.annotationType = QueryParam.class;
            this.bindingName = ann.value().isEmpty() ? parameter.getName() : ann.value();
        } else {
            this.annotationType = null;
            this.bindingName = "";
        }
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
