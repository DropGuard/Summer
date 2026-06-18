package summer.aot;

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
                ClassName controllerClass = safeClassName(controller.qualifiedName);
                registerBody.addStatement("$T $N = context.getBean($T.class)",
                        controllerClass, varName,
                        controllerClass);

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

		// Extract parameters
		for (RouteInfo.ParamInfo param : route.params) {
			if (param.binding == RouteInfo.ParamBinding.PATH) {
				body.add("$T $N = ctx.request().pathParam($S);\n", resolveParamType(param.type), param.name,
						param.name);
			} else if (param.binding == RouteInfo.ParamBinding.QUERY) {
				body.add("$T $N = ctx.request().queryParam($S);\n", resolveParamType(param.type), param.name,
						param.name);
			} else if (param.binding == RouteInfo.ParamBinding.BODY) {
				String method = param.validated ? "validatedBody" : "body";
				body.add("$T $N = ctx.$L($T.class);\n", resolveParamType(param.type), param.name, method,
						ClassName.bestGuess(param.type));
			} else if (param.binding == RouteInfo.ParamBinding.PAGEABLE) {
				body.add("$T $N = $T.of(\n", ClassName.bestGuess("summer.web.Pageable"), param.name,
						ClassName.bestGuess("summer.web.PageRequest"));
				body.indent();
				body.add("parseIntOrDefault(ctx.queryParam(\"page\"), 0),\n");
				body.add("parseIntOrDefault(ctx.queryParam(\"size\"), 20),\n");
				body.add("$T.parse(ctx.queryParam(\"sort\"))\n", ClassName.bestGuess("summer.web.Sort"));
				body.unindent();
				body.add(");\n");
			}
		}

// Controller methods require HttpContext as first parameter.
            // Validation happens in BeanEnrichment.
            StringBuilder args = new StringBuilder("ctx");
            for (int i = 0; i < route.params.size(); i++) {
                args.append(", ");
                args.append(route.params.get(i).name);
            }

		if (route.returnType.equals("void")) {
			body.add("$N.$L($N);\n", controllerVar, route.methodName, args.toString());
		} else {
			body.add("$T result = $N.$L($N);\n", ClassName.bestGuess(route.returnType), controllerVar, route.methodName,
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
		return switch (type) {
			case "int" -> com.palantir.javapoet.TypeName.INT;
			case "long" -> com.palantir.javapoet.TypeName.LONG;
			case "double" -> com.palantir.javapoet.TypeName.DOUBLE;
			case "boolean" -> com.palantir.javapoet.TypeName.BOOLEAN;
			case "float" -> com.palantir.javapoet.TypeName.FLOAT;
			case "short" -> com.palantir.javapoet.TypeName.SHORT;
			case "byte" -> com.palantir.javapoet.TypeName.BYTE;
			case "char" -> com.palantir.javapoet.TypeName.CHAR;
default -> ClassName.bestGuess(type);
            };
        }

        private static ClassName safeClassName(String qualifiedName) {
            return ClassName.bestGuess(qualifiedName.replace('$', '.'));
        }
    }
