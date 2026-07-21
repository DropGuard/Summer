package summer.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.BeanContainer;
import summer.core.Discovery;
import summer.core.Engine;
import summer.core.RuntimeDiMarker;
import summer.core.bean.BeanDefinition;
import summer.core.bean.ConfigPropertiesBean;
import summer.core.bean.MockedBean;
import summer.core.bean.ModuleIndex;
import summer.core.bean.RouteInfo;
import summer.core.bean.SharedConditionEvaluator;
import summer.core.bean.SharedDependencyResolver;
import summer.core.config.ConfigBinder;
import summer.core.config.ConfigurationProperties;
import summer.core.config.TypeConverter;
import summer.core.validation.Validator;

/**
 * Builds {@link BeanContainer} for the Runtime DI engine.
 *
 * <pre>{@code
 * BeanContainer ctx = DiEngine.create(Engine.RUNTIME);
 * }</pre>
 *
 * <p>
 * For isolated builds (tests), use {@code Testing.build()} over the full test
 * universe, or {@code Testing.build()} for the TCK path.
 * </p>
 */
public final class RuntimeBeanContainerBuilder {

	private static final Logger log = LoggerFactory.getLogger(RuntimeBeanContainerBuilder.class);

	private RuntimeBeanContainerBuilder() {
	}

	/**
	 * Builds a {@link BeanContainer} over the test universe: the whole application
	 * (every production bean) plus the test-class beans on the classpath. Mirrors
	 * Quarkus' {@code @QuarkusTest} universe — no seed list, no module narrowing.
	 * Test beans are discovered exactly like production beans.
	 *
	 * @param mocks
	 *            mocked beans produced from {@code @Mock} parameters (internal)
	 * @return immutable bean container
	 */
	public static BeanContainer build(List<MockedBean> mocks) {
		return build(testUniverse(), mocks, java.util.Map.of());
	}

	/**
	 * Convenience overload for the no-mock, no-override test universe (equivalent
	 * to {@link #build(List)} with an empty mock list). Retained so existing tests
	 * that call {@code build()} without mocks keep compiling.
	 *
	 * @return immutable bean container over the full test universe
	 */
	public static BeanContainer build() {
		return build(List.of());
	}

	/**
	 * Production bootstrap entry point, invoked reflectively by
	 * {@code DiEngine.create} for the Runtime engine (dev mode / IDE). Discovers
	 * the full application components and registers the boot-time external beans
	 * supplied by {@code SummerApplication} (e.g. the ordered middleware list from
	 * {@code apply(...)}). Test containers never use this overload — they go
	 * through {@link #build(List)} and must not hand-register beans.
	 */
	public static BeanContainer build(Object... externalBeans) {
		ModuleIndex moduleIndex = testUniverse();
		return initialize(moduleIndex, List.of(), java.util.Map.of(), externalBeans);
	}

	/**
	 * Test container build with explicit {@code @TestProfile} overrides over the
	 * full test universe. The universe is always the whole application plus test
	 * beans on the classpath — discovery never loads a class outside it, and both
	 * engines observe the same candidate set.
	 *
	 * @param mocks
	 *            mocked beans produced from {@code @Mock} parameters (internal)
	 * @param overrides
	 *            resolved {@code @TestProfile} content (empty map when none)
	 * @return immutable bean container
	 */
	public static BeanContainer build(List<MockedBean> mocks, java.util.Map<String, Object> overrides) {
		return build(testUniverse(), mocks, overrides);
	}

	/**
	 * Builds a {@link BeanContainer} over a caller-supplied narrow
	 * {@link IndexView} (e.g. a seed + transitive-closure index from
	 * {@code @SummerTest(classes=...)}). The classpath-type mapping is collapsed to
	 * a single synthetic module so {@code discoverComponents} enumerates exactly
	 * the classes in the supplied index — no production or unrelated test beans
	 * leak in. Discovery, mock, and profile handling are identical to the
	 * full-universe path; only the candidate set shrinks.
	 */
	public static BeanContainer build(IndexView narrowIndex, List<MockedBean> mocks,
			java.util.Map<String, Object> overrides) {
		ModuleIndex moduleIndex = ModuleIndex.single(narrowIndex);
		return initialize(moduleIndex, mocks, overrides);
	}

	/**
	 * Builds a {@link BeanContainer} over a caller-supplied {@link ModuleIndex}.
	 * The test container uses the test universe index; the production path is not
	 * exposed here (production startup uses {@code SummerApplication} /
	 * {@code SummerMojo}, which read only {@code jandex.idx}).
	 *
	 * @param moduleIndex
	 *            the module index to discover from (test universe)
	 * @param mocks
	 *            mocked beans produced from {@code @Mock} parameters (internal)
	 * @param overrides
	 *            resolved {@code @TestProfile} content (empty map when none)
	 * @return immutable bean container
	 */
	public static BeanContainer build(ModuleIndex moduleIndex, List<MockedBean> mocks,
			java.util.Map<String, Object> overrides) {
		return initialize(moduleIndex, mocks, overrides);
	}

	/** The test universe index: application beans plus test-class beans. */
	private static ModuleIndex testUniverse() {
		return JandexIndexLoader.testIndex();
	}

	private static BeanContainer initialize(ModuleIndex moduleIndex, List<MockedBean> mocks,
			java.util.Map<String, Object> overrides, Object... externalBeans) {
		BeanContainer.Builder builder = new BeanContainer.Builder();

		builder.register(RuntimeDiMarker.class, new RuntimeDiMarker());

		// ── Phase 1: Discovery ──────────────────────────────────────
		// Unified discovery shared with the AOT engine: both engines observe the
		// same candidate set, so dual-engine parity is structural, not conventional.
		// Discovery yields enriched BeanDefinitions directly (no Class.forName at
		// discovery time — class loading is deferred to the instantiator, matching
		// AOT). It already registers AotDiMarker; we add the Runtime marker and the
		// IndexView bean (needed by @Bean method param resolution).
		List<BeanDefinition> candidates = Discovery.discover(moduleIndex);
		candidates.add(new BeanDefinition(RuntimeDiMarker.class.getName(), "RuntimeDiMarker"));
		// Register IndexView so the dependency resolver can find it for @Bean method
		// params
		candidates.add(new BeanDefinition(IndexView.class.getName(), IndexView.class.getSimpleName()));

		// ── Phase 2: Evaluation ─────────────────────────────────────
		// Evaluate @ConditionalOnBean and @Replaces against the scoped candidate
		// set, and remove real beans whose type is mocked. Condition targets and
		// mock replacements are evaluated identically on both engines (the AOT path
		// feeds the same MockedBean target types into SharedConditionEvaluator).
		Set<String> mockedTypeNames = mocks.stream().map(MockedBean::targetTypeName)
				.collect(java.util.stream.Collectors.toSet());
		new SharedConditionEvaluator().evaluate(candidates, mockedTypeNames, moduleIndex);

		// ── Phase 3: Resolution ─────────────────────────────────────
		// Bind, sort, instantiate. A single BindingContext carries the parsed
		// YAML (cached) and any @TestProfile overrides — no ThreadLocal, no
		// remove() discipline. The overrides parameter is consumed only here, at
		// the binding step; it does not thread through the rest of initialization.
		ConfigBinder.BindingContext ctx = ConfigBinder.BindingContext.of(overrides);
		bindConfigurationProperties(candidates, builder, ctx);
		BeanDefinitionFactory.populateInterceptors(candidates);

		// Pre-build exception handler metadata for RuntimeExceptionHandlerRegistrar.
		// This eliminates reflection-based @ExceptionHandler scanning at registration
		// time.
		RuntimeExceptionHandlerRegistrar.setPrebuiltHandlers(candidates);

		SharedDependencyResolver resolver = new SharedDependencyResolver();
		List<BeanDefinition> sorted = resolver.resolve(candidates, mocks);

		Map<String, List<String>> interceptorMap = BeanDefinitionFactory.buildInterceptorMap(candidates);
		Map<String, Set<String>> interceptorBindingMap = new HashMap<>();
		for (BeanDefinition bd : candidates) {
			if (!bd.interceptorBindingAnnotations.isEmpty()) {
				interceptorBindingMap.put(bd.qualifiedName, bd.interceptorBindingAnnotations);
			}
		}
		BeanInstantiator instantiator = new BeanInstantiator(builder, interceptorMap, interceptorBindingMap);

		builder.register(IndexView.class, moduleIndex.index());
		// Register mocked instances under their declared target type (and the
		// target's interfaces) so dependent beans inject the mock. The real bean of
		// each target type was already removed at discovery stage, so this never
		// collides with a real instance.
		for (MockedBean mocked : mocks) {
			builder.register(mocked.targetType(), mocked.instance());
			for (Class<?> iface : mocked.targetType().getInterfaces()) {
				builder.register(iface, mocked.instance());
			}
		}
		for (BeanDefinition beanDef : sorted) {
			instantiator.instantiateFromDefinition(beanDef);
		}
		// Collect route metadata from candidates for route registration
		List<RouteInfo> allRoutes = candidates.stream().flatMap(bd -> bd.routes.stream()).toList();
		builder.routes(allRoutes);

		runValidators(builder);

		// Boot-time external beans (production only): register under concrete class.
		for (Object bean : externalBeans) {
			if (bean != null) {
				builder.register(bean.getClass(), bean);
			}
		}

		return builder.build(Engine.RUNTIME);
	}

	// ---- @ConfigurationProperties binding ----

	/**
	 * Binds every {@code @ConfigurationProperties} bean discovered in the candidate
	 * set. Reads the Jandex-extracted {@link ConfigPropertiesBean} (prefix +
	 * {@code @DefaultValue} metadata) so binding is identical to the AOT engine,
	 * which consumes the same {@link BeanDefinition} fields. Class loading is
	 * deferred to here (one {@code Class.forName} per config bean), keeping
	 * discovery reflection-free.
	 */
	private static void bindConfigurationProperties(List<BeanDefinition> candidates, BeanContainer.Builder builder,
			ConfigBinder.BindingContext ctx) {
		for (BeanDefinition beanDef : candidates) {
			if (!(beanDef instanceof ConfigPropertiesBean configBean)) {
				continue;
			}
			Class<?> configClass;
			try {
				configClass = Class.forName(configBean.qualifiedName);
			} catch (ClassNotFoundException e) {
				log.debug("[Summer] Could not load @ConfigurationProperties class: {}", configBean.qualifiedName);
				continue;
			}
			if (builder.peek(configClass) != null) {
				continue;
			}
			ConfigurationProperties props = configClass.getAnnotation(ConfigurationProperties.class);
			if (props == null) {
				continue;
			}
			// Convert the Jandex-extracted @DefaultValue strings into their declared
			// types and feed them through the BindingContext, mirroring AOT's baked-in
			// defaults — no separate reflection pass needed.
			Map<String, Object> defaults = new HashMap<>();
			for (var entry : configBean.defaultValues.entrySet()) {
				String fieldType = configBean.fieldTypes.get(entry.getKey());
				if (fieldType != null) {
					try {
						defaults.put(entry.getKey(), TypeConverter.convert(entry.getValue(), Class.forName(fieldType)));
					} catch (Exception ignored) {
						// leave unconverted; ConfigBinder handles the raw string
					}
				}
			}
			ConfigBinder.BindingContext perClassCtx = defaults.isEmpty()
					? ctx
					: ConfigBinder.BindingContext.of(defaults, ctx.overrides());
			Object instance = ConfigBinder.bind(perClassCtx, props.prefix(), configClass);
			builder.register(configClass, instance);
			log.debug("[Summer] Bound @ConfigurationProperties: {} (prefix='{}')", configClass.getSimpleName(),
					props.prefix());
		}
	}

	// ---- Validation ----

	@SuppressWarnings("unchecked")
	private static void runValidators(BeanContainer.Builder builder) {
		for (Object bean : builder.singletons().values()) {
			if (bean instanceof Validator<?> validator) {
				Class<?> targetType = validator.targetType();
				Object target = builder.peek(targetType);
				if (target != null) {
					((Validator<Object>) validator).validate(target);
				}
			}
		}
	}
}
