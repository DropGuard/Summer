package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;

/**
 * Generates {@code GeneratedExceptionHandlerAdapter} — the AOT counterpart of the runtime engine's
 * exception-handler registrar.
 *
 * <p>Uses {@link BeanDefinition#exceptionHandlerMethods} to discover which beans carry handlers,
 * then reads method signatures from Jandex for code generation (parameter types, names, exceptions
 * — these are code shape concerns, not bean metadata).
 */
final class ExceptionHandlerAdapterGenerator {

    private static final DotName EXCEPTION_HANDLER_DOT =
            DotName.createSimple("com.github.dropguard.summer.web.annotation.ExceptionHandler");
    private static final DotName HTTP_CONTEXT_DOT =
            DotName.createSimple("com.github.dropguard.summer.web.HttpContext");
    private static final ClassName EXCEPTION_REGISTRY =
            ClassName.get("com.github.dropguard.summer.web", "ExceptionRegistry");
    private static final ClassName BEAN_CONTAINER =
            ClassName.get("com.github.dropguard.summer.core", "BeanContainer");
    private static final ClassName EXCEPTION_HANDLER_REGISTRAR =
            ClassName.get("com.github.dropguard.summer.web", "ExceptionHandlerRegistrar");
    private static final ClassName REQUEST_ATTRIBUTES =
            ClassName.get("com.github.dropguard.summer.web", "RequestAttributes");
    private static final String PACKAGE = "com.github.dropguard.summer.aot.generated";
    private static final String CLASS_NAME = "GeneratedExceptionHandlerAdapter";

    public ExceptionHandlerAdapterGenerator() {}

    public void generate(List<BeanDefinition> beans, IndexView index, java.io.File outputDir)
            throws IOException {
        CodeBlock body = buildRegisterHandlersBody(beans, index);

        TypeSpec.Builder adapter =
                TypeSpec.classBuilder(CLASS_NAME)
                        .addModifiers(
                                javax.lang.model.element.Modifier.PUBLIC,
                                javax.lang.model.element.Modifier.FINAL)
                        .addSuperinterface(EXCEPTION_HANDLER_REGISTRAR);

        if (body.isEmpty()) {
            // Generate a no-op implementation so the class always exists
            // (AotContextGenerator always references GeneratedExceptionHandlerAdapter).
            adapter.addMethod(
                    MethodSpec.methodBuilder("registerHandlers")
                            .addAnnotation(Override.class)
                            .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                            .addParameter(EXCEPTION_REGISTRY, "registry")
                            .addParameter(BEAN_CONTAINER, "context")
                            .addCode("")
                            .build());
        } else {
            adapter.addMethod(
                    MethodSpec.methodBuilder("registerHandlers")
                            .addAnnotation(Override.class)
                            .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                            .addParameter(EXCEPTION_REGISTRY, "registry")
                            .addParameter(BEAN_CONTAINER, "context")
                            .addCode(body)
                            .build());
        }

        JavaFile.builder(PACKAGE, adapter.build()).indent("    ").build().writeTo(outputDir);
    }

    private CodeBlock buildRegisterHandlersBody(List<BeanDefinition> beans, IndexView index) {
        CodeBlock.Builder code = CodeBlock.builder();
        Set<String> declaredHandlers = new HashSet<>();

        for (BeanDefinition bean : beans) {
            // Phase-1 filter: skip beans without exception handlers entirely.
            // This is exactly what BD.exceptionHandlerMethods is for — fast O(1)
            // rejection without touching Jandex.
            if (bean.exceptionHandlerMethods.isEmpty()) {
                continue;
            }

            // Phase-2 detail: read method signatures from Jandex for code generation.
            // Signature details (param types, names, exceptions) are code-shape
            // concerns, not bean metadata — Jandex is the right source.
            ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
            if (ci == null) {
                continue;
            }

            ClassName handlerClass = AotTypeNames.safeClassName(bean.qualifiedName);
            String handlerVar = bean.variableName;

            for (MethodInfo method : ci.methods()) {
                AnnotationInstance ann = method.annotation(EXCEPTION_HANDLER_DOT);
                if (ann == null) {
                    continue;
                }

                String exceptionClassName = ann.value().asClass().name().toString();
                ClassName exceptionType = AotTypeNames.safeClassName(exceptionClassName);

                // Build args: inspect parameter types from Jandex
                StringBuilder args = new StringBuilder();
                for (MethodParameterInfo param : method.parameters()) {
                    if (args.length() > 0) {
                        args.append(", ");
                    }
                    boolean isContext =
                            param.type().name() != null
                                    && HTTP_CONTEXT_DOT.equals(param.type().name());
                    args.append(isContext ? "httpCtx" : "ex");
                }

                if (declaredHandlers.add(bean.qualifiedName)) {
                    code.addStatement(
                            "$T $N = context.getBean($T.class)",
                            handlerClass,
                            handlerVar,
                            handlerClass);
                }
                code.beginControlFlow("registry.register($T.class, httpCtx ->", exceptionType);
                code.addStatement(
                        "$T ex = ($T) httpCtx.request().getAttribute($T.LAST_EXCEPTION)",
                        exceptionType,
                        exceptionType,
                        REQUEST_ATTRIBUTES);
                code.addStatement("$N.$N($N)", handlerVar, method.name(), args);
                code.endControlFlow(")");
            }
        }
        return code.build();
    }
}
