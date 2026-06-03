package summer.plugin;

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

	RouteAdapterGenerator() {
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
		List<BeanDefinition> controllers = beans.stream()
				.filter(b -> !b.routes.isEmpty())
				.toList();

		if (controllers.isEmpty()) {
			return;
		}

		// Generate the registerControllers method body
		CodeBlock.Builder registerBody = CodeBlock.builder();

		for (BeanDefinition controller : controllers) {
			String varName = controller.variableName;
			registerBody.addStatement("$T $N = context.getBean($T.class)",
					ClassName.bestGuess(controller.qualifiedName),
					varName,
					ClassName.bestGuess(controller.qualifiedName));

			for (RouteInfo route : controller.routes) {
				CodeBlock handlerBody = generateHandlerBody(route, varName);
				registerBody.addStatement("router.$L($S, $L)",
						route.httpMethod.toLowerCase(),
						route.path,
						handlerBody);
			}
		}

		TypeSpec routeRegistrar = TypeSpec.classBuilder("GeneratedAnnotationRouterAdapter")
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.FINAL)
				.addSuperinterface(ClassName.get(WEB_PACKAGE, "RouteRegistrar"))
				.addField(ClassName.get(WEB_PACKAGE, "Router"), "router",
						javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL)
				.addField(ClassName.get(CORE_PACKAGE, "ApplicationContext"), "context",
						javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL)
				.addField(ClassName.get(WEB_PACKAGE, "ExceptionRegistry"), "exceptionRegistry",
						javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL)
				.addMethod(MethodSpec.constructorBuilder()
						.addModifiers(javax.lang.model.element.Modifier.PUBLIC)
						.addParameter(ClassName.get(WEB_PACKAGE, "Router"), "router")
						.addParameter(ClassName.get(CORE_PACKAGE, "ApplicationContext"), "context")
						.addParameter(ClassName.get(WEB_PACKAGE, "ExceptionRegistry"), "exceptionRegistry")
						.addStatement("this.router = router")
						.addStatement("this.context = context")
						.addStatement("this.exceptionRegistry = exceptionRegistry")
						.build())
				.addMethod(MethodSpec.methodBuilder("registerControllers")
						.addAnnotation(Override.class)
						.addModifiers(javax.lang.model.element.Modifier.PUBLIC)
						.addCode(registerBody.build())
						.build())
				.build();

		JavaFile.builder("summer.core.aot", routeRegistrar).build().writeTo(outputDir);
	}

	/**
	 * Generate handler lambda body for a route.
	 */
	private CodeBlock generateHandlerBody(RouteInfo route, String controllerVar) {
		CodeBlock.Builder body = CodeBlock.builder();

		// Extract parameters
		for (RouteInfo.ParamInfo param : route.params) {
			if (param.binding == RouteInfo.ParamBinding.PATH) {
				body.addStatement("$T $N = ctx.request().pathParam($S)",
						resolveParamType(param.type),
						param.name,
						param.name);
			} else if (param.binding == RouteInfo.ParamBinding.QUERY) {
				body.addStatement("$T $N = ctx.request().queryParam($S)",
						resolveParamType(param.type),
						param.name,
						param.name);
			} else if (param.binding == RouteInfo.ParamBinding.BODY) {
				String method = param.validated ? "validatedBody" : "body";
				body.addStatement("$T $N = ctx.$L($T.class)",
						resolveParamType(param.type),
						param.name,
						method,
						ClassName.bestGuess(param.type));
			}
		}

		// Call controller method
		StringBuilder args = new StringBuilder();
		for (int i = 0; i < route.params.size(); i++) {
			if (i > 0)
				args.append(", ");
			args.append(route.params.get(i).name);
		}

		if (route.returnType.equals("void")) {
			body.addStatement("$N.$L($N)", controllerVar, route.methodName, args.toString());
		} else {
			body.addStatement("$T result = $N.$L($N)",
					ClassName.bestGuess(route.returnType),
					controllerVar,
					route.methodName,
					args.toString());
			body.addStatement("ctx.ok(result)");
		}

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
}
