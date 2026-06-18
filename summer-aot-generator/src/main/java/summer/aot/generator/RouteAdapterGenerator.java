package summer.aot.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.List;

/**
 * Generates web route adapter for AOT mode.
 *
 * <p>
 * In AOT mode, routes are registered statically instead of via reflection. This
 * generator creates a RouteRegistrar that wires up controllers and exception
 * handlers at compile time.
 * </p>
 */
public final class RouteAdapterGenerator {

	private static final String WEB_PACKAGE = "summer.web";
	private static final String CORE_PACKAGE = "summer.core";
	private static final ClassName TYPE_CONVERTER = ClassName.get("summer.core.config", "TypeConverter");

	public RouteAdapterGenerator() {
	}

	/**
	 * Generate a RouteRegistrar implementation for AOT mode.
	 *
	 * @param beans
	 *            list of bean definitions
	 * @param outputDir
	 *            directory to write generated source files
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
			String varName = controller.variableName;
			registerBody.addStatement("$T $N = context.getBean($T.class)",
					TypeNameUtil.classNameFromString(controller.qualifiedName), varName,
					TypeNameUtil.classNameFromString(controller.qualifiedName));

			for (RouteInfo route : controller.routes) {
				CodeBlock handlerBody = generateHandlerBody(route, varName);

				registerBody.add("builder.$L($S, ", route.httpMethod.toLowerCase(), route.path);
				registerBody.add(handlerBody);
				registerBody.add(");\n");
			}
		}

		TypeSpec routeRegistrar = TypeSpec.classBuilder("GeneratedAnnotationRouterAdapter")
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.FINAL)
				.addSuperinterface(ClassName.get(WEB_PACKAGE, "RouteRegistrar"))
				.addMethod(MethodSpec.methodBuilder("registerControllers").addAnnotation(Override.class)
						.addModifiers(javax.lang.model.element.Modifier.PUBLIC)
						.addParameter(ClassName.get(WEB_PACKAGE, "HttpRouter", "Builder"), "builder")
						.addParameter(ClassName.get(CORE_PACKAGE, "BeanContainer"), "context")
						.addCode(registerBody.build()).build())
				.addMethod(buildParseIntOrDefault()).build();

		JavaFile.builder("summer.core.aot", routeRegistrar).build().writeTo(outputDir);
	}

	private MethodSpec buildParseIntOrDefault() {
		return MethodSpec.methodBuilder("parseIntOrDefault")
				.addModifiers(javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.STATIC)
				.returns(int.class).addParameter(String.class, "value").addParameter(int.class, "defaultValue")
				.beginControlFlow("if (value == null || value.isBlank())").addStatement("return defaultValue")
				.endControlFlow().beginControlFlow("try")
				.addStatement("return Math.max(0, Integer.parseInt(value.trim()))")
				.nextControlFlow("catch ($T e)", NumberFormatException.class).addStatement("return defaultValue")
				.endControlFlow().build();
	}

	/**
	 * Generate handler lambda body for a route.
	 */
	private CodeBlock generateHandlerBody(RouteInfo route, String controllerVar) {
		CodeBlock.Builder body = CodeBlock.builder();

		body.add("ctx -> {\n");
		body.indent();

		// Extract parameters from request
		for (RouteInfo.ParamInfo param : route.params) {
			if (param.binding == RouteInfo.ParamBinding.PATH) {
				if (isStringType(param.type)) {
					body.add("$T $N = ctx.request().pathParam($S);\n", resolveParamType(param.type), param.name,
							param.name);
				} else {
					body.add("$T $N = ($T) $T.convert(ctx.request().pathParam($S), $T.class);\n",
							resolveParamType(param.type), param.name, resolveParamType(param.type),
							TYPE_CONVERTER, param.name, TypeNameUtil.classNameFromString(param.type));
				}
			} else if (param.binding == RouteInfo.ParamBinding.QUERY) {
				if (isStringType(param.type)) {
					body.add("$T $N = ctx.request().queryParam($S);\n", resolveParamType(param.type), param.name,
							param.name);
				} else {
					body.add("$T $N = ($T) $T.convert(ctx.request().queryParam($S), $T.class);\n",
							resolveParamType(param.type), param.name, resolveParamType(param.type),
							TYPE_CONVERTER, param.name, TypeNameUtil.classNameFromString(param.type));
				}
			} else if (param.binding == RouteInfo.ParamBinding.BODY) {
				String method = param.validated ? "validatedBody" : "body";
				body.add("$T $N = ctx.$L($T.class);\n", resolveParamType(param.type), param.name, method,
						TypeNameUtil.classNameFromString(param.type));
			} else if (param.binding == RouteInfo.ParamBinding.PAGEABLE) {
				body.add("$T pageableProps = $T.bind($S, $T.class)\n",
						TypeNameUtil.classNameFromString("summer.core.config.PageableProperties"),
						ClassName.get("summer.core.config", "ConfigBinder"),
						"summer.pageable",
						TypeNameUtil.classNameFromString("summer.core.config.PageableProperties"));
				body.add(";\n");
				body.add("$T $N = $T.of(\n", TypeNameUtil.classNameFromString("summer.web.Pageable"), param.name,
						TypeNameUtil.classNameFromString("summer.web.PageRequest"));
				body.indent();
				body.add("parseIntOrDefault(ctx.queryParam(\"page\"), pageableProps.defaultPage()),\n");
				body.add("parseIntOrDefault(ctx.queryParam(\"size\"), pageableProps.defaultSize()),\n");
				body.add("$T.parse(ctx.queryParam(\"sort\"))\n", TypeNameUtil.classNameFromString("summer.web.Sort"));
				body.unindent();
				body.add(");\n");
			}
		}

		// Call controller method — ctx first, then extracted params in declaration order
		StringBuilder args = new StringBuilder("ctx");
		for (RouteInfo.ParamInfo param : route.params) {
			args.append(", ").append(param.name);
		}

		if (route.returnType.equals("void")) {
			body.add("$N.$L($N);\n", controllerVar, route.methodName, args.toString());
		} else {
			body.add("$T result = $N.$L($N);\n", TypeNameUtil.classNameFromString(route.returnType), controllerVar, route.methodName,
					args.toString());
			body.add("ctx.ok(result);\n");
		}

		body.unindent();
		body.add("}");

		return body.build();
	}

	/**
	 * Resolve parameter type string to TypeName.
	 */
	private com.palantir.javapoet.TypeName resolveParamType(String type) {
		return TypeNameUtil.fromString(type);
	}

	private static boolean isStringType(String type) {
		return "java.lang.String".equals(type) || "String".equals(type);
	}
}
