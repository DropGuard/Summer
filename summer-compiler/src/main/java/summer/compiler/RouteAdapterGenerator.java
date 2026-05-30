package summer.compiler;

import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Generates the {@code GeneratedAnnotationRouterAdapter} class that registers
 * web routes and exception handlers at compile time. Extracted from
 * SummerProcessor to separate web route generation from bean collection.
 */
final class RouteAdapterGenerator {

	private RouteAdapterGenerator() {
	}

	/**
	 * Generates the route adapter if any @RestController or @ExceptionHandler beans
	 * exist.
	 */
	static void generate(List<AptBeanDefinition> beans, ProcessingEnvironment processingEnv) {
		TypeElement restControllerType = processingEnv.getElementUtils()
				.getTypeElement("summer.web.annotation.RestController");
		if (restControllerType == null)
			return;

		List<AptBeanDefinition> controllers = beans.stream().filter(b -> b instanceof AptBeanDefinition)
				.map(b -> (AptBeanDefinition) b)
				.filter(b -> AnnotationHelper.hasAnnotation(b.typeElement, "summer.web.annotation.RestController"))
				.toList();

		List<AptBeanDefinition> componentsWithExceptionHandlers = new ArrayList<>();
		for (AptBeanDefinition b : beans) {
			if (!(b instanceof AptBeanDefinition apt))
				continue;
			boolean hasHandler = ElementFilter.methodsIn(apt.typeElement.getEnclosedElements()).stream()
					.anyMatch(m -> AnnotationHelper.hasAnnotation(m, "summer.web.annotation.ExceptionHandler"));
			if (hasHandler) {
				componentsWithExceptionHandlers.add(apt);
			}
		}

		if (controllers.isEmpty() && componentsWithExceptionHandlers.isEmpty()) {
			return;
		}

		TypeSpec.Builder adapterBuilder = TypeSpec.classBuilder("GeneratedAnnotationRouterAdapter")
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL)
				.addSuperinterface(ClassName.get("summer.web", "RouteRegistrar"));

		adapterBuilder.addField(FieldSpec
				.builder(ClassName.get("org.slf4j", "Logger"), "log", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
				.initializer("$T.getLogger(GeneratedAnnotationRouterAdapter.class)",
						ClassName.get("org.slf4j", "LoggerFactory"))
				.build());

		ClassName contextClass = ClassName.get("summer.core.aot", "GeneratedAotContext");
		adapterBuilder.addField(contextClass, "context", Modifier.PRIVATE, Modifier.FINAL);
		adapterBuilder.addField(ClassName.get("summer.web", "Router"), "router", Modifier.PRIVATE, Modifier.FINAL);
		adapterBuilder.addField(ClassName.get("summer.web", "ExceptionRegistry"), "exceptionRegistry", Modifier.PRIVATE,
				Modifier.FINAL);

		MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
				.addParameter(ClassName.get("summer.web", "Router"), "router")
				.addParameter(ClassName.get("summer.core", "ApplicationContext"), "context")
				.addParameter(ClassName.get("summer.web", "ExceptionRegistry"), "exceptionRegistry")
				.addStatement("this.context = (GeneratedAotContext) context").addStatement("this.router = router")
				.addStatement("this.exceptionRegistry = exceptionRegistry").build();
		adapterBuilder.addMethod(constructor);

		MethodSpec.Builder registerMethod = MethodSpec.methodBuilder("registerControllers")
				.addAnnotation(Override.class).addModifiers(Modifier.PUBLIC);

		Types types = processingEnv.getTypeUtils();
		TypeMirror throwableType = processingEnv.getElementUtils().getTypeElement("java.lang.Throwable").asType();

		// Register controller routes
		for (AptBeanDefinition controller : controllers) {
			String basePath = AnnotationHelper.getAnnotationStringValue(controller.typeElement,
					"summer.web.annotation.RestController", processingEnv);
			ClassName controllerClass = ClassName.get(controller.typeElement);

			for (ExecutableElement method : ElementFilter.methodsIn(controller.typeElement.getEnclosedElements())) {
				String httpMethod = null;
				String methodPath = null;

				if (AnnotationHelper.hasAnnotation(method, "summer.web.annotation.Get")) {
					httpMethod = "GET";
					methodPath = AnnotationHelper.getAnnotationStringValue(method, "summer.web.annotation.Get",
							processingEnv);
				} else if (AnnotationHelper.hasAnnotation(method, "summer.web.annotation.Post")) {
					httpMethod = "POST";
					methodPath = AnnotationHelper.getAnnotationStringValue(method, "summer.web.annotation.Post",
							processingEnv);
				} else if (AnnotationHelper.hasAnnotation(method, "summer.web.annotation.Put")) {
					httpMethod = "PUT";
					methodPath = AnnotationHelper.getAnnotationStringValue(method, "summer.web.annotation.Put",
							processingEnv);
				} else if (AnnotationHelper.hasAnnotation(method, "summer.web.annotation.Delete")) {
					httpMethod = "DELETE";
					methodPath = AnnotationHelper.getAnnotationStringValue(method, "summer.web.annotation.Delete",
							processingEnv);
				}

				if (httpMethod != null) {
					String combinedPath = combinePaths(basePath, methodPath);
					CodeBlock args = buildMethodCallArgs(method, types, throwableType, processingEnv);

					CodeBlock.Builder lambdaBody = CodeBlock.builder();
					lambdaBody.add("$T controller = this.context.getBean($T.class);\n", controllerClass,
							controllerClass);
					if (method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
						lambdaBody.add("controller.$N($L);\n", method.getSimpleName().toString(), args);
						lambdaBody.add("return \"\";\n");
					} else {
						lambdaBody.add("return controller.$N($L);\n", method.getSimpleName().toString(), args);
					}

					registerMethod.addCode("{\n");
					registerMethod.addStatement("$T handler = ctx -> {\n$L}", ClassName.get("summer.web", "Handler"),
							lambdaBody.build());

					List<TypeMirror> classMiddlewares = AnnotationHelper.getAnnotationClassListValue(
							controller.typeElement, "summer.web.annotation.Use", processingEnv);
					List<TypeMirror> methodMiddlewares = AnnotationHelper.getAnnotationClassListValue(method,
							"summer.web.annotation.Use", processingEnv);
					List<TypeMirror> allMiddlewares = new ArrayList<>();
					allMiddlewares.addAll(classMiddlewares);
					allMiddlewares.addAll(methodMiddlewares);
					Collections.reverse(allMiddlewares);

					for (TypeMirror mw : allMiddlewares) {
						registerMethod.addStatement("handler = this.context.getBean($T.class).apply(handler)",
								TypeName.get(mw));
					}

					registerMethod.addStatement("this.router.register($S, $S, handler)", httpMethod, combinedPath);
					registerMethod.addStatement("log.info($S, $S + $S)", "Route registered (Static AOT): {} {}",
							httpMethod, combinedPath);
					registerMethod.addCode("}\n");
				}
			}
		}

		// Register exception handlers
		for (AptBeanDefinition component : componentsWithExceptionHandlers) {
			ClassName componentClass = ClassName.get(component.typeElement);

			for (ExecutableElement method : ElementFilter.methodsIn(component.typeElement.getEnclosedElements())) {
				if (AnnotationHelper.hasAnnotation(method, "summer.web.annotation.ExceptionHandler")) {
					List<TypeMirror> exceptionClasses = AnnotationHelper.getAnnotationClassListValue(method,
							"summer.web.annotation.ExceptionHandler", processingEnv);
					CodeBlock args = buildMethodCallArgs(method, types, throwableType, processingEnv);

					for (TypeMirror exc : exceptionClasses) {
						CodeBlock.Builder lambdaBody = CodeBlock.builder();
						lambdaBody.add("$T bean = this.context.getBean($T.class);\n", componentClass, componentClass);
						if (method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
							lambdaBody.add("bean.$N($L);\n", method.getSimpleName().toString(), args);
							lambdaBody.add("return \"\";\n");
						} else {
							lambdaBody.add("return bean.$N($L);\n", method.getSimpleName().toString(), args);
						}

						registerMethod.addCode("{\n");
						registerMethod.addStatement("$T handler = ctx -> {\n$L}",
								ClassName.get("summer.web", "Handler"), lambdaBody.build());
						registerMethod.addStatement("this.exceptionRegistry.register($T.class, handler)",
								TypeName.get(exc));
						registerMethod.addStatement("log.info($S, $T.class.getSimpleName())",
								"Exception Handler registered (Static AOT): {}", TypeName.get(exc));
						registerMethod.addCode("}\n");
					}
				}
			}
		}

		adapterBuilder.addMethod(registerMethod.build());

		JavaFile javaFile = JavaFile.builder("summer.core.aot", adapterBuilder.build()).indent("    ").build();
		try {
			javaFile.writeTo(processingEnv.getFiler());
			processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
					"[Summer AOT] Generated summer.core.aot.GeneratedAnnotationRouterAdapter");
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
					"Failed to write GeneratedAnnotationRouterAdapter: " + e.getMessage());
		}
	}

	// --- Private helpers ---

	private static CodeBlock buildMethodCallArgs(ExecutableElement method, Types types, TypeMirror throwableType,
			ProcessingEnvironment processingEnv) {
		CodeBlock.Builder builder = CodeBlock.builder();
		List<? extends VariableElement> params = method.getParameters();
		for (int i = 0; i < params.size(); i++) {
			if (i > 0)
				builder.add(", ");
			VariableElement param = params.get(i);
			TypeMirror paramType = param.asType();

			if (paramType.toString().equals("summer.web.WebContext")) {
				builder.add("ctx");
			} else if (paramType.toString().equals("summer.web.Request")) {
				builder.add("ctx.request()");
			} else if (AnnotationHelper.hasAnnotation(param, "summer.web.annotation.PathParam")) {
				String val = AnnotationHelper.getAnnotationStringValue(param, "summer.web.annotation.PathParam",
						processingEnv);
				builder.add("ctx.request().pathParam($S)", val);
			} else if (types.isAssignable(paramType, throwableType)) {
				builder.add("($T) ctx.request().getAttribute(\"last_exception\")", TypeName.get(paramType));
			} else {
				if (AnnotationHelper.hasAnnotation(param, "summer.web.annotation.Valid")) {
					builder.add("ctx.validatedBody($T.class)", TypeName.get(types.erasure(paramType)));
				} else {
					builder.add("ctx.body($T.class)", TypeName.get(types.erasure(paramType)));
				}
			}
		}
		return builder.build();
	}

	private static String combinePaths(String basePath, String methodPath) {
		if (basePath == null)
			basePath = "";
		if (methodPath == null)
			methodPath = "";
		if (basePath.isEmpty())
			return normalizePath(methodPath);
		if (methodPath.isEmpty())
			return normalizePath(basePath);
		String normalizedBase = normalizePath(basePath);
		String normalizedMethod = normalizePath(methodPath);
		if (normalizedBase.endsWith("/") && normalizedMethod.startsWith("/")) {
			return normalizedBase + normalizedMethod.substring(1);
		} else if (!normalizedBase.endsWith("/") && !normalizedMethod.startsWith("/")) {
			return normalizedBase + "/" + normalizedMethod;
		} else {
			return normalizedBase + normalizedMethod;
		}
	}

	private static String normalizePath(String path) {
		if (path == null || path.isEmpty())
			return "/";
		if (!path.startsWith("/"))
			return "/" + path;
		return path;
	}
}
