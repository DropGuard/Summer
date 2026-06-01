package summer.plugin;

import com.palantir.javapoet.ClassName;
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
		// Find all @RestController beans
		List<BeanDefinition> controllers = beans.stream()
				.filter(b -> b.interfaceNames.contains("summer.web.annotation.RestController")
						|| b.qualifiedName.contains("Controller"))
				.toList();

		if (controllers.isEmpty()) {
			return;
		}

		// Generate a simple RouteRegistrar that registers controllers
		TypeSpec routeRegistrar = TypeSpec.classBuilder("GeneratedAnnotationRouterAdapter")
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.FINAL)
				.addSuperinterface(ClassName.get("summer.web", "RouteRegistrar"))
				.addField(ClassName.get("summer.web", "Router"), "router", javax.lang.model.element.Modifier.PRIVATE,
						javax.lang.model.element.Modifier.FINAL)
				.addField(ClassName.get("summer.core", "ApplicationContext"), "context",
						javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL)
				.addField(ClassName.get("summer.web", "ExceptionRegistry"), "exceptionRegistry",
						javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL)
				.addMethod(MethodSpec.constructorBuilder().addModifiers(javax.lang.model.element.Modifier.PUBLIC)
						.addParameter(ClassName.get("summer.web", "Router"), "router")
						.addParameter(ClassName.get("summer.core", "ApplicationContext"), "context")
						.addParameter(ClassName.get("summer.web", "ExceptionRegistry"), "exceptionRegistry")
						.addStatement("this.router = router").addStatement("this.context = context")
						.addStatement("this.exceptionRegistry = exceptionRegistry").build())
				.addMethod(MethodSpec.methodBuilder("registerControllers").addAnnotation(Override.class)
						.addModifiers(javax.lang.model.element.Modifier.PUBLIC)
						.addComment("TODO: Register routes statically for AOT mode").build())
				.build();

		JavaFile.builder("summer.core.aot", routeRegistrar).build().writeTo(outputDir);
	}
}
