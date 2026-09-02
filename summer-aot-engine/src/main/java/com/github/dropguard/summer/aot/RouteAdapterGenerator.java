package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.RouteInfo;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates web route adapter for AOT mode.
 *
 * <p>In AOT mode, routes are registered statically instead of via reflection. This generator
 * creates a RouterAdapter that wires up controllers and exception handlers at compile time.
 */
@Internal
public final class RouteAdapterGenerator {

    private static final Logger log = LoggerFactory.getLogger(RouteAdapterGenerator.class);

    private static final String WEB_PACKAGE = "com.github.dropguard.summer.web";
    private static final String CORE_PACKAGE = "com.github.dropguard.summer.core";

    public RouteAdapterGenerator() {}

    /**
     * Generate a RouterAdapter implementation for AOT mode.
     *
     * <p>Controller instances resolve through the birth record (the {@code _instantiatedBeans} map
     * populated by the generated wire method) — never through {@code getBean}, which by the
     * one-bean-one-form contract rejects AOP-bound concrete types. For an AOP-bound controller the
     * handler variable is typed as the interface declaring the route method, so the call dispatches
     * through the generated proxy and interception applies; a route method not exposed on any
     * interface fails at GENERATION time.
     *
     * @param beans list of bean definitions
     * @param index Jandex index of the application classes
     * @param outputDir directory to write generated source files
     */
    public void generate(List<BeanDefinition> beans, IndexView index, java.io.File outputDir)
            throws IOException {
        // Find all @RestController beans with routes
        List<BeanDefinition> controllers = beans.stream().filter(b -> !b.routes.isEmpty()).toList();

        if (controllers.isEmpty()) {
            return;
        }

        // Generate the registerControllers method body
        CodeBlock.Builder registerBody = CodeBlock.builder();

        for (BeanDefinition controller : controllers) {
            log.debug("[Summer] Generating route adapter for {}", controller.qualifiedName);
            String varName = controller.variableName;
            ClassName controllerClass = AotTypeNames.safeClassName(controller.qualifiedName);
            ClassName dispatchType = dispatchType(controller, controllerClass, index);
            registerBody.addStatement(
                    "$T $N = ($T) incarnations.get($S)",
                    dispatchType,
                    varName,
                    dispatchType,
                    controller.qualifiedName);

            for (RouteInfo route : controller.routes) {
                CodeBlock handlerBody = generateHandlerBody(route, varName);

                registerBody.add("builder.$L($S, ", route.httpMethod.toLowerCase(), route.path);
                registerBody.add(handlerBody);
                registerBody.add(");\n");
            }
        }

        TypeSpec routeRegistrar =
                TypeSpec.classBuilder("GeneratedAnnotationRouterAdapter")
                        .addModifiers(
                                javax.lang.model.element.Modifier.PUBLIC,
                                javax.lang.model.element.Modifier.FINAL)
                        .addSuperinterface(ClassName.get(WEB_PACKAGE, "RouterAdapter"))
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
                                        .build())
                        .addMethod(
                                MethodSpec.methodBuilder("registerControllers")
                                        .addAnnotation(Override.class)
                                        .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                                        .addParameter(
                                                ClassName.get(WEB_PACKAGE, "HttpRouter", "Builder"),
                                                "builder")
                                        .addParameter(
                                                ClassName.get(CORE_PACKAGE, "BeanContainer"),
                                                "context")
                                        .addCode(registerBody.build())
                                        .build())
                        .build();

        JavaFile.builder("com.github.dropguard.summer.aot.generated", routeRegistrar)
                .build()
                .writeTo(outputDir);
    }

    /** Generate handler lambda body for a route. */
    private CodeBlock generateHandlerBody(RouteInfo route, String controllerVar) {
        CodeBlock.Builder body = CodeBlock.builder();

        body.add("ctx -> {\n");
        body.indent();

        // Extract parameters
        for (RouteInfo.ParamInfo param : route.params) {
            String key = param.bindingName.isEmpty() ? param.name : param.bindingName;
            body.add(paramDeclaration(param, key));
        }

        // Controller methods require HttpContext as first parameter.
        // Validation happens in BeanEnrichment.
        StringBuilder args = new StringBuilder("ctx");
        for (int i = 0; i < route.params.size(); i++) {
            args.append(", ");
            args.append(route.params.get(i).name);
        }

        body.add("$N.$L($N);\n", controllerVar, route.methodName, args.toString());

        body.unindent();
        body.add("}");

        return body.build();
    }

    /** Resolve parameter type string to TypeName. */

    /**
     * Maps a {@link RouteInfo.ParamBinding} to the annotation class literal that identifies that
     * parameter kind in generated code.
     *
     * <p>Returns a {@code $T.class} code block for bindings that are recognised by annotation —
     * {@code PATH} → {@code PathParam}, {@code QUERY} → {@code QueryParam}. Every other binding
     * returns {@code null} because those parameters carry no annotation marker: they are resolved
     * by type or position (e.g. a pageable is matched by its parameter type, a body by position),
     * and the resolver chain treats {@code null} as "no annotation to match against".
     *
     * <p>The block is passed into the generated {@code RouteInfoHandlerParam} constructor, so the
     * AOT engine resolves parameters through the same {@code HttpParameterResolverChain} the
     * runtime uses — keeping annotation-driven matching identical across both engines.
     */
    /**
     * Parameter declaration emitted into the handler lambda.
     *
     * <p>A switch <em>expression</em> over {@code ParamBinding}, deliberately: adding a binding
     * constant without a case here is a compile error in this generator — the compiler replaces
     * what used to be a runtime else-throw guard against silently-generated broken code.
     */
    private CodeBlock paramDeclaration(RouteInfo.ParamInfo param, String key) {
        return switch (param.binding) {
            case PATH ->
                    CodeBlock.of(
                            "$T $N = $L;\n",
                            TypeReads.typeName(param.type),
                            param.name,
                            TypeReads.httpParse(param.type, "ctx.request().pathParam", key));
            case QUERY ->
                    CodeBlock.of(
                            "$T $N = $L;\n",
                            TypeReads.typeName(param.type),
                            param.name,
                            TypeReads.httpParse(param.type, "ctx.request().queryParam", key));
            case BODY -> {
                String method = param.validated ? "validatedBody" : "body";
                yield CodeBlock.of(
                        "$T $N = ctx.$L($T.class);\n",
                        TypeReads.typeName(param.type),
                        param.name,
                        method,
                        AotTypeNames.safeClassName(param.type));
            }
            case RESOLVER ->
                    // The resolver chain is the user-extensible path (@Replaces swaps resolvers),
                    // so these parameters resolve through the same chain the runtime uses,
                    // keeping @Replaces behaviour identical across engines. PATH/QUERY/BODY stay
                    // inline because they have no swappable resolver.
                    CodeBlock.of(
                            "$T $N = ($T) context.getBean($T.class).resolve(ctx, new $T($T.class,"
                                    + " $S, $L, $L));\n",
                            TypeReads.typeName(param.type),
                            param.name,
                            TypeReads.typeName(param.type),
                            ClassName.get(
                                    "com.github.dropguard.summer.web",
                                    "HttpParameterResolverChain"),
                            ClassName.get(
                                    "com.github.dropguard.summer.web", "RouteInfoHandlerParam"),
                            AotTypeNames.safeClassName(param.type),
                            key,
                            annotationType(param.binding),
                            param.validated);
        };
    }

    private static com.palantir.javapoet.CodeBlock annotationType(
            com.github.dropguard.summer.core.bean.RouteInfo.ParamBinding binding) {
        return switch (binding) {
            case PATH ->
                    com.palantir.javapoet.CodeBlock.of(
                            "$T.class",
                            ClassName.get(
                                    "com.github.dropguard.summer.web.annotation", "PathParam"));
            case QUERY ->
                    com.palantir.javapoet.CodeBlock.of(
                            "$T.class",
                            ClassName.get(
                                    "com.github.dropguard.summer.web.annotation", "QueryParam"));
            default -> com.palantir.javapoet.CodeBlock.of("null");
        };
    }

    /**
     * The type a route variable must carry for dispatch: the concrete class for unbound beans, the
     * interface declaring the route method for AOP-bound ones. Generation-time fail-fast — an
     * interface-declared route method is the price of interface-based proxies, the same rule
     * constructor injection already follows.
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
                for (RouteInfo route : bean.routes) {
                    if (method.name().equals(route.methodName)
                            && method.parameters().size() == route.params.size() + 1) {
                        requirePublicInterface(iface, bean);
                        return AotTypeNames.safeClassName(ifaceName);
                    }
                }
            }
        }
        throw new IllegalStateException(
                "Generation failed: route methods of "
                        + bean.qualifiedName
                        + " are not declared on any of its interfaces. Summer proxies are"
                        + " interface-based — declare the route method on the bean's interface so"
                        + " it can dispatch through the proxy.");
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
