package summer.aot;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jboss.jandex.IndexView;
import summer.core.bean.BeanDefinition;

/**
 * Generates a {@code GeneratedAotContext} class that uses the unified
 * {@link summer.core.BeanContainer} abstractions.
 *
 * <p>
 * Dependencies are injected via constructor — no mutable state.
 * </p>
 */
public final class AotContextGenerator {

	public static final String PACKAGE = "summer.core.aot";
	public static final String CLASS_NAME = "GeneratedAotContext";

	private static final String CORE_PACKAGE = "summer.core";
	private static final ClassName BEAN_CONTAINER = ClassName.get(CORE_PACKAGE, "BeanContainer");
	private static final ClassName BEAN_CONTAINER_BUILDER = ClassName.get(CORE_PACKAGE, "BeanContainer", "Builder");
	private static final ClassName ENGINE = ClassName.get(CORE_PACKAGE, "Engine");
	private static final ClassName AOT_DI_MARKER = ClassName.get(CORE_PACKAGE, "AotDiMarker");
	private static final ClassName CONFIG_BINDER = ClassName.get("summer.core.config", "ConfigBinder");
	private static final ClassName DEFAULT_VALUE_RESOLVER = ClassName.get("summer.core.config", "DefaultValueResolver");
	private static final ClassName ROUTE_ADAPTER = ClassName.get(PACKAGE, "GeneratedAnnotationRouterAdapter");
	private static final ClassName ROUTE_REGISTRAR = ClassName.get("summer.web", "RouteRegistrar");
	private static final ClassName EXCEPTION_HANDLER_ADAPTER = ClassName.get(PACKAGE,
			"GeneratedExceptionHandlerAdapter");
	private static final ClassName EXCEPTION_HANDLER_REGISTRAR = ClassName.get("summer.web",
			"ExceptionHandlerRegistrar");

	private final IndexView index;
	private final File outputDir;
	private final WireMethodGenerator wireGen;

	public AotContextGenerator(IndexView index, File outputDir, WireMethodGenerator wireGen) {
		this.index = index;
		this.outputDir = outputDir;
		this.wireGen = wireGen;
	}

	public void generate(List<BeanDefinition> sortedBeans) throws IOException {
		generate(sortedBeans, CLASS_NAME);
	}

	/**
	 * Generates the AOT context class under an explicit name. The default
	 * {@link #CLASS_NAME} ({@code GeneratedAotContext}) is used by the production
	 * path (generated at build time by {@code summer-maven-plugin}); tests pass a
	 * scope/profile-derived name so two different test containers never collide on
	 * the JVM's single-load-per-name class cache.
	 *
	 * @param sortedBeans
	 *            topologically-sorted bean definitions
	 * @param className
	 *            generated class name (without package)
	 */
	public void generate(List<BeanDefinition> sortedBeans, String className) throws IOException {
		new ExceptionHandlerAdapterGenerator().generate(sortedBeans, index, outputDir);

		JavaFile javaFile = buildJavaFile(sortedBeans, className);
		javaFile.writeTo(outputDir);
	}

	private JavaFile buildJavaFile(List<BeanDefinition> sortedBeans) {
		return buildJavaFile(sortedBeans, CLASS_NAME);
	}

	private JavaFile buildJavaFile(List<BeanDefinition> sortedBeans, String className) {
		TypeSpec.Builder type = TypeSpec.classBuilder(className).addModifiers(javax.lang.model.element.Modifier.PUBLIC,
				javax.lang.model.element.Modifier.FINAL);

		// Legacy fields for backward compatibility, no longer checked by DiEngine but
		// kept to avoid breaking old compiled binaries that might read them.
		type.addField(FieldSpec
				.builder(String.class, "BEAN_FINGERPRINT", javax.lang.model.element.Modifier.PUBLIC,
						javax.lang.model.element.Modifier.STATIC, javax.lang.model.element.Modifier.FINAL)
				.initializer("$S", "dev-mode-fallback").build());

		MethodSpec staticCreate = buildCreateMethod(sortedBeans);
		TypeSpec spec = type.addMethod(staticCreate).build();
		return JavaFile.builder(PACKAGE, spec).indent("    ").build();
	}

	private MethodSpec buildCreateMethod(List<BeanDefinition> sortedBeans) {
		MethodSpec.Builder method = MethodSpec.methodBuilder("build")
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.STATIC)
				.addParameter(Object[].class, "externalBeans").varargs(true).returns(BEAN_CONTAINER)
				.addException(Exception.class);
		method.addStatement("$T builder = new $T()", BEAN_CONTAINER_BUILDER, BEAN_CONTAINER_BUILDER);
		method.beginControlFlow("if (externalBeans != null)");
		method.beginControlFlow("for (Object bean : externalBeans)");
		method.addStatement("builder.register(bean.getClass(), bean)");
		method.endControlFlow();
		method.endControlFlow();
		method.addStatement("builder.register($T.class, new $T())", AOT_DI_MARKER, AOT_DI_MARKER);

		method.addCode("\n");
		method.addComment("Setup @DefaultValue resolver");
		method.addStatement("$T.setDefaultValueResolver(($T) Class.forName($S).getField($S).get(null))", CONFIG_BINDER,
				DEFAULT_VALUE_RESOLVER, "summer.runtime.RuntimeDefaultValueResolver", "INSTANCE");
		method.addCode("\n");

		wireGen.generateWireMethod(method, sortedBeans);

		// Route adapter
		if (sortedBeans.stream().anyMatch(b -> !b.routes.isEmpty())) {
			method.addCode("\n");
			method.addComment("Register route adapter");
			method.addStatement("$T _routeAdapter = new $T()", ROUTE_ADAPTER, ROUTE_ADAPTER);
			method.addStatement("builder.register($T.class, _routeAdapter)", ROUTE_REGISTRAR);
		}

		// Exception handler adapter — always present (empty if no handlers)
		method.addCode("\n");
		method.addComment("Register exception handler adapter");
		method.addStatement("$T _ehAdapter = new $T()", EXCEPTION_HANDLER_ADAPTER, EXCEPTION_HANDLER_ADAPTER);
		method.addStatement("builder.register($T.class, _ehAdapter)", EXCEPTION_HANDLER_REGISTRAR);

		method.addCode("\n");
		method.addStatement("return builder.build($T.AOT)", ENGINE);

		return method.build();
	}

}
