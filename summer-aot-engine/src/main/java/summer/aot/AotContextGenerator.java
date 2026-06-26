package summer.aot;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.util.List;
import summer.core.bean.BeanDefinition;

/**
 * Generates a {@code GeneratedAotContext} class that uses the unified
 * {@link summer.core.BeanRegistry} / {@link summer.core.BeanContainer}
 * abstractions.
 */
public final class AotContextGenerator {

	public static final String PACKAGE = "summer.core.aot";
	public static final String CLASS_NAME = "GeneratedAotContext";

	org.jboss.jandex.IndexView index;

	private static final String CORE_PACKAGE = "summer.core";
	private static final ClassName BEAN_REGISTRY = ClassName.get(CORE_PACKAGE, "BeanRegistry");
	private static final ClassName BEAN_CONTAINER = ClassName.get(CORE_PACKAGE, "BeanContainer");
	private static final ClassName DI_ENGINE = ClassName.get(CORE_PACKAGE, "DiEngine");
	private static final ClassName ENGINE = ClassName.get(CORE_PACKAGE, "Engine");
	private static final ClassName AOT_DI_MARKER = ClassName.get(CORE_PACKAGE, "AotDiMarker");
	private static final ClassName VALIDATOR = ClassName.get("summer.core.validation", "Validator");
	private static final ClassName CONFIG_BINDER = ClassName.get("summer.core.config", "ConfigBinder");
	private static final ClassName DEFAULT_VALUE_RESOLVER = ClassName.get("summer.core.config", "DefaultValueResolver");
	private static final ClassName ROUTE_ADAPTER = ClassName.get(PACKAGE, "GeneratedAnnotationRouterAdapter");
	private static final ClassName ROUTE_REGISTRAR = ClassName.get("summer.web", "RouteRegistrar");
	private static final ClassName EXCEPTION_HANDLER_ADAPTER = ClassName.get(PACKAGE,
			"GeneratedExceptionHandlerAdapter");
	private static final ClassName EXCEPTION_HANDLER_REGISTRAR = ClassName.get("summer.web",
			"ExceptionHandlerRegistrar");

	TypeName parseTypeName(String typeName) {
		if (typeName.startsWith("["))
			return ClassName.get(Object.class);
		return switch (typeName) {
			case "byte" -> TypeName.BYTE;
			case "short" -> TypeName.SHORT;
			case "int" -> TypeName.INT;
			case "long" -> TypeName.LONG;
			case "float" -> TypeName.FLOAT;
			case "double" -> TypeName.DOUBLE;
			case "boolean" -> TypeName.BOOLEAN;
			case "char" -> TypeName.CHAR;
			default -> bestGuessWithoutDollar(typeName);
		};
	}

	private static ClassName bestGuessWithoutDollar(String name) {
		return ClassName.bestGuess(name.replace('$', '.'));
	}

	public void generate(List<BeanDefinition> sortedBeans, java.io.File outputDir, org.jboss.jandex.IndexView index)
			throws java.io.IOException {
		this.index = index;

		new ExceptionHandlerAdapterGenerator().generate(sortedBeans, index, outputDir);

		JavaFile javaFile = buildJavaFile(sortedBeans);
		javaFile.writeTo(outputDir);
	}

	private JavaFile buildJavaFile(List<BeanDefinition> sortedBeans) {
		TypeSpec.Builder type = TypeSpec.classBuilder(CLASS_NAME).addModifiers(javax.lang.model.element.Modifier.PUBLIC,
				javax.lang.model.element.Modifier.FINAL);

		MethodSpec staticCreate = buildCreateMethod(sortedBeans);
		TypeSpec spec = type.addMethod(staticCreate).build();
		return JavaFile.builder(PACKAGE, spec).indent("    ").build();
	}

	private MethodSpec buildCreateMethod(List<BeanDefinition> sortedBeans) {
		MethodSpec.Builder method = MethodSpec.methodBuilder("build")
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.STATIC)
				.returns(BEAN_CONTAINER).addException(Exception.class);

		method.addStatement("$T registry = new $T()", BEAN_REGISTRY, BEAN_REGISTRY);
		method.addStatement("registry.registerSingleton($T.class, new $T())", AOT_DI_MARKER, AOT_DI_MARKER);

		method.addCode("\n");
		method.addComment("Setup @DefaultValue resolver");
		method.addStatement("$T.setDefaultValueResolver(($T) Class.forName($S).getField($S).get(null))", CONFIG_BINDER,
				DEFAULT_VALUE_RESOLVER, "summer.runtime.RuntimeDefaultValueResolver", "INSTANCE");
		method.addCode("\n");

		new WireMethodGenerator(this).generateWireMethod(method, sortedBeans);

		// Route adapter
		if (sortedBeans.stream().anyMatch(b -> !b.routes.isEmpty())) {
			method.addCode("\n");
			method.addComment("Register route adapter");
			method.addStatement("$T _routeAdapter = new $T()", ROUTE_ADAPTER, ROUTE_ADAPTER);
			method.addStatement("registry.registerSingleton($T.class, _routeAdapter)", ROUTE_REGISTRAR);
		}

		// Exception handler adapter — always present (empty if no handlers)
		method.addCode("\n");
		method.addComment("Register exception handler adapter");
		method.addStatement("$T _ehAdapter = new $T()", EXCEPTION_HANDLER_ADAPTER, EXCEPTION_HANDLER_ADAPTER);
		method.addStatement("registry.registerSingleton($T.class, _ehAdapter)", EXCEPTION_HANDLER_REGISTRAR);

		new WireMethodGenerator(this).emitRowMapperRegistrations(method, this.index, null, sortedBeans);

		method.addCode("\n");
		method.addStatement("return $T.create(registry, $T.AOT)", BEAN_CONTAINER, ENGINE);

		return method.build();
	}
}
