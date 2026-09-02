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
import java.util.Map;
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
 * <p>Handler instances resolve through the birth record (the {@code _instantiatedBeans} map
 * populated by the generated wire method, the counterpart of {@code BeanInstantiator}'s {@code
 * InstantiatedBeans}) — never through {@code getBean}, which by the one-bean-one-form contract
 * rejects AOP-bound concrete types. For an AOP-bound bean the handler variable is typed as the
 * interface that declares the handler method, so the call dispatches through the generated proxy
 * and interception applies; a handler method not exposed on any interface fails at GENERATION time.
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
                        .addSuperinterface(EXCEPTION_HANDLER_REGISTRAR)
                        .addField(
                                com.palantir.javapoet.FieldSpec.builder(
                                                com.palantir.javapoet.ParameterizedTypeName.get(
                                                        ClassName.get(Map.class),
                                                        ClassName.get(String.class),
                                                        ClassName.get(Object.class)),
                                                "incarnations",
                                                javax.lang.model.element.Modifier.PRIVATE,
                                                javax.lang.model.element.Modifier.FINAL)
                                        .build())
                        .addMethod(
                                MethodSpec.constructorBuilder()
                                        .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                                        .addParameter(
                                                com.palantir.javapoet.ParameterizedTypeName.get(
                                                        ClassName.get(Map.class),
                                                        ClassName.get(String.class),
                                                        ClassName.get(Object.class)),
                                                "incarnations")
                                        .addStatement("this.incarnations = incarnations")
                                        .build());

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
            // One-bean-one-form: for an AOP-bound bean the incarnation IS the proxy, which is
            // only invokable through its interfaces — dispatch type is the interface declaring
            // the handler method, resolved once per bean.
            ClassName dispatchType = dispatchType(bean, handlerClass, index);

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
                            "$T $N = ($T) incarnations.get($S)",
                            dispatchType,
                            handlerVar,
                            dispatchType,
                            bean.qualifiedName);
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

    /**
     * The type a handler variable must carry for dispatch: the concrete class for unbound beans,
     * the interface declaring the handler method for AOP-bound ones. Generation-time fail-fast — an
     * interface-declared handler is the price of interface-based proxies, the same rule constructor
     * injection already follows.
     */
    private static ClassName dispatchType(
            BeanDefinition bean, ClassName concreteType, IndexView index) {
        if (!bean.needsProxy()) {
            return concreteType;
        }
        for (String ifaceName : bean.interfaceNames) {
            ClassInfo iface = index.getClassByName(DotName.createSimple(ifaceName));
            if (iface == null) {
                continue;
            }
            for (MethodInfo method : iface.methods()) {
                for (BeanDefinition.ExceptionHandlerEntry eh : bean.exceptionHandlerMethods) {
                    if (method.name().equals(eh.methodName())
                            && method.parameters().size() == eh.parameterCount()) {
                        requirePublicInterface(iface, bean);
                        return AotTypeNames.safeClassName(ifaceName);
                    }
                }
            }
        }
        throw new IllegalStateException(
                "Generation failed: exception handler method of "
                        + bean.qualifiedName
                        + " is not declared on any of its interfaces. Summer proxies are"
                        + " interface-based — declare the @ExceptionHandler method on the bean's"
                        + " interface so it can dispatch through the proxy.");
    }

    /**
     * The dispatched method's declaring interface must be public: the generated adapters and the
     * proxy invocation handler call it from other packages, and reflective access checks the
     * declaring class's visibility. Enforced here so the failure surfaces at BUILD time.
     */
    private static void requirePublicInterface(ClassInfo iface, BeanDefinition bean) {
        // ACC_PUBLIC is JVMS 4.1 access-flag bit 0x0001 — Jandex stores the raw class flags,
        // and java.lang.reflect.Modifier is banned in this module (reflection confinement).
        if ((iface.flags() & 0x0001) == 0) {
            throw new IllegalStateException(
                    "Generation failed: "
                            + bean.qualifiedName
                            + " dispatches through the interface "
                            + iface.name().toString()
                            + ", which is not public. Proxy dispatch and the generated adapters"
                            + " invoke it from other packages — make the interface public (a"
                            + " public nested interface also works).");
        }
    }
}
