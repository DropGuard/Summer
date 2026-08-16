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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates web route adapter for AOT mode.
 *
 * <p>In AOT mode, routes are registered statically instead of via reflection. This generator
 * creates a RouteRegistrar that wires up controllers and exception handlers at compile time.
 */
@Internal
public final class RouteAdapterGenerator {

    private static final Logger log = LoggerFactory.getLogger(RouteAdapterGenerator.class);

    private static final String WEB_PACKAGE = "com.github.dropguard.summer.web";
    private static final String CORE_PACKAGE = "com.github.dropguard.summer.core";

    public RouteAdapterGenerator() {}

    /**
     * Generate a RouteRegistrar implementation for AOT mode.
     *
     * @param beans list of bean definitions
     * @param outputDir directory to write generated source files
     */
    public void generate(List<BeanDefinition> beans, java.io.File outputDir) throws IOException {
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
            registerBody.addStatement(
                    "$T $N = context.getBean($T.class)", controllerClass, varName, controllerClass);

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
                        .addSuperinterface(ClassName.get(WEB_PACKAGE, "RouteRegistrar"))
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
            if (param.binding == RouteInfo.ParamBinding.PATH) {
                body.add(
                        "$T $N = $L;\n",
                        TypeReads.typeName(param.type),
                        param.name,
                        TypeReads.httpParse(param.type, "ctx.request().pathParam", key));
            } else if (param.binding == RouteInfo.ParamBinding.QUERY) {
                body.add(
                        "$T $N = $L;\n",
                        TypeReads.typeName(param.type),
                        param.name,
                        TypeReads.httpParse(param.type, "ctx.request().queryParam", key));
            } else if (param.binding == RouteInfo.ParamBinding.BODY) {
                String method = param.validated ? "validatedBody" : "body";
                body.add(
                        "$T $N = ctx.$L($T.class);\n",
                        TypeReads.typeName(param.type),
                        param.name,
                        method,
                        ClassName.bestGuess(param.type));
            } else if (param.binding == RouteInfo.ParamBinding.PAGEABLE) {
                // @Pageable is the one user-extensible resolver (@Replaces swaps it), so
                // resolve it through the same HttpParameterResolverChain the runtime uses.
                // This keeps @Replaces behaviour identical across engines. PATH/QUERY/BODY
                // stay inline because they have no swappable resolver.
                // annotationType is null for pageable params — resolvers match by the
                // parameter TYPE (DefaultPageResolver.supports), mirroring RuntimeHandlerParam.
                body.add(
                        "$T $N = ($T) context.getBean($T.class).resolve(ctx, new $T($T.class, $S,"
                                + " $L, $L));\n",
                        TypeReads.typeName(param.type),
                        param.name,
                        TypeReads.typeName(param.type),
                        ClassName.get(
                                "com.github.dropguard.summer.web", "HttpParameterResolverChain"),
                        ClassName.get("com.github.dropguard.summer.web", "RouteInfoHandlerParam"),
                        ClassName.bestGuess(param.type),
                        param.bindingName,
                        annotationType(param.binding),
                        param.validated);
            }
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
}
