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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.bean.BeanDefinition;
import summer.core.bean.MockedBean;

/**
 * Generates a {@code GeneratedAotContext} class that uses the unified
 * {@link summer.core.BeanContainer} abstractions.
 *
 * <p>
 * Dependencies are injected via constructor — no mutable state.
 * </p>
 */
public final class AotContextGenerator {

	private static final Logger log = LoggerFactory.getLogger(AotContextGenerator.class);

	public static final String PACKAGE = "summer.core.aot";
	public static final String CLASS_NAME = "GeneratedAotContext";

	private static final String CORE_PACKAGE = "summer.core";
	private static final ClassName BEAN_CONTAINER = ClassName.get(CORE_PACKAGE, "BeanContainer");
	private static final ClassName BEAN_CONTAINER_BUILDER = ClassName.get(CORE_PACKAGE, "BeanContainer", "Builder");
	private static final ClassName ENGINE = ClassName.get(CORE_PACKAGE, "Engine");
	private static final ClassName AOT_DI_MARKER = ClassName.get(CORE_PACKAGE, "AotDiMarker");
	private static final ClassName CONFIG_BINDER = ClassName.get("summer.core.config", "ConfigBinder");
	private static final ClassName ROUTE_ADAPTER = ClassName.get(PACKAGE, "GeneratedAnnotationRouterAdapter");
	private static final ClassName ROUTE_REGISTRAR = ClassName.get("summer.web", "RouteRegistrar");
	private static final ClassName EXCEPTION_HANDLER_ADAPTER = ClassName.get(PACKAGE,
			"GeneratedExceptionHandlerAdapter");
	private static final ClassName EXCEPTION_HANDLER_REGISTRAR = ClassName.get("summer.web",
			"ExceptionHandlerRegistrar");
	private static final ClassName MOCKED_BEAN = ClassName.get("summer.core.bean", "MockedBean");

	private final IndexView index;
	private final File outputDir;
	private final WireMethodGenerator wireGen;
	private final java.util.Map<String, Object> profileOverrides;

	public AotContextGenerator(IndexView index, File outputDir, WireMethodGenerator wireGen) {
		this(index, outputDir, wireGen, java.util.Map.of());
	}

	public AotContextGenerator(IndexView index, File outputDir, WireMethodGenerator wireGen,
			java.util.Map<String, Object> profileOverrides) {
		this.index = index;
		this.outputDir = outputDir;
		this.wireGen = wireGen;
		this.profileOverrides = profileOverrides != null ? profileOverrides : java.util.Map.of();
	}

	public void generate(List<BeanDefinition> sortedBeans, MockedBean[] mocks) throws IOException {
		generate(sortedBeans, CLASS_NAME, mocks);
	}

	/**
	 * Production entry point (invoked by {@code summer-maven-plugin}). Emits only
	 * the boot {@code build(Object...)} method — production never consumes mocks,
	 * so the typed {@code build(MockedBean[])} test channel is not generated here.
	 */
	public void generate(List<BeanDefinition> sortedBeans) throws IOException {
		generate(sortedBeans, CLASS_NAME, null);
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
	public void generate(List<BeanDefinition> sortedBeans, String className, MockedBean[] mocks) throws IOException {
		log.debug("[Summer] Generating AOT context {} for {} beans", className, sortedBeans.size());
		new ExceptionHandlerAdapterGenerator().generate(sortedBeans, index, outputDir);

		JavaFile javaFile = buildJavaFile(sortedBeans, className, mocks);
		javaFile.writeTo(outputDir);
	}

	private JavaFile buildJavaFile(List<BeanDefinition> sortedBeans, MockedBean[] mocks) {
		return buildJavaFile(sortedBeans, CLASS_NAME, mocks);
	}

	private JavaFile buildJavaFile(List<BeanDefinition> sortedBeans, String className, MockedBean[] mocks) {
		TypeSpec.Builder type = TypeSpec.classBuilder(className).addModifiers(javax.lang.model.element.Modifier.PUBLIC,
				javax.lang.model.element.Modifier.FINAL);

		// Legacy fields for backward compatibility, no longer checked by DiEngine but
		// kept to avoid breaking old compiled binaries that might read them.
		type.addField(FieldSpec
				.builder(String.class, "BEAN_FINGERPRINT", javax.lang.model.element.Modifier.PUBLIC,
						javax.lang.model.element.Modifier.STATIC, javax.lang.model.element.Modifier.FINAL)
				.initializer("$S", "dev-mode-fallback").build());

		// Production path (mocks == null) emits only the boot build(Object...) method.
		// Test path (mocks != null) additionally emits the typed build(MockedBean[])
		// channel so a concrete-class @Mock resolves under its declared type.
		TypeSpec.Builder spec = type.addMethod(buildProductionCreateMethod(sortedBeans));
		if (mocks != null) {
			spec.addMethod(buildCreateMethod(sortedBeans, mocks));
		}
		TypeSpec built = spec.build();
		return JavaFile.builder(PACKAGE, built).indent("    ").build();
	}

	/**
	 * Test entry point: {@code build(MockedBean[])}. Each mock is registered under
	 * its declared {@link MockedBean#targetType()} (and every interface that type
	 * implements) rather than under {@code instance.getClass()} — the Mockito
	 * proxy's own class. The real definition of the target type has already been
	 * removed at discovery stage, so injection matches on the declared type and a
	 * concrete-class {@code @Mock} resolves correctly.
	 */
	private MethodSpec buildCreateMethod(List<BeanDefinition> sortedBeans, MockedBean[] mocks) {
		MethodSpec.Builder method = MethodSpec.methodBuilder("build")
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.STATIC)
				.addParameter(MockedBean[].class, "mocks").returns(BEAN_CONTAINER).addException(Exception.class);
		method.addStatement("$T builder = new $T()", BEAN_CONTAINER_BUILDER, BEAN_CONTAINER_BUILDER);
		method.beginControlFlow("if (mocks != null)");
		method.beginControlFlow("for ($T mocked : mocks)", MOCKED_BEAN);
		method.addStatement("builder.register(mocked.targetType(), mocked.instance())");
		method.beginControlFlow("for (Class<?> iface : mocked.targetType().getInterfaces())");
		method.addStatement("builder.register(iface, mocked.instance())");
		method.endControlFlow();
		method.endControlFlow();
		method.endControlFlow();
		emitSharedBody(method, sortedBeans);
		return method.build();
	}

	/**
	 * Production entry point: {@code build(Object[])}. Retained for the boot path
	 * ({@code SummerApplication} passes the ordered middleware list from
	 * {@code apply(...)} as an external bean). External beans are registered under
	 * their own concrete class — the only caller is framework startup, never the
	 * test channel, so this stays an untyped {@code Object[]} by design.
	 */
	private MethodSpec buildProductionCreateMethod(List<BeanDefinition> sortedBeans) {
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
		emitSharedBody(method, sortedBeans);
		return method.build();
	}

	/**
	 * Shared tail: marker, @DefaultValue resolver, wire method, route + handler
	 * adapters.
	 */
	private void emitSharedBody(MethodSpec.Builder method, List<BeanDefinition> sortedBeans) {
		method.addStatement("builder.register($T.class, new $T())", AOT_DI_MARKER, AOT_DI_MARKER);

		// Engine-provided beans (IndexView, RuntimeDiMarker, ...) arrive as synthetic
		// beans in the candidate list and are registered by WireMethodGenerator — no
		// hand-written registration here.
		wireGen.generateWireMethod(method, sortedBeans, profileOverrides);

		// AOT row mappers: emit inline (zero-reflection) RowMapper lambdas for every
		// @RowModel record, registered directly on JdbcTemplate. This is the AOT
		// counterpart of data-jdbc's reflective RowMapperRegistrar, which is
		// @ConditionalOnBean(RuntimeDiMarker) and therefore skipped on the AOT engine
		// (it uses AotDiMarker) — see WireMethodGenerator#emitRowMapperRegistrations.
		wireGen.emitRowMapperRegistrations(method, index, null, sortedBeans);

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
	}

}
