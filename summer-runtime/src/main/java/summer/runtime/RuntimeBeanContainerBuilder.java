package summer.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.core.RuntimeDiMarker;
import summer.core.bean.BeanDefinition;
import summer.core.bean.ModuleIndex;
import summer.core.bean.RouteInfo;
import summer.core.bean.Scope;
import summer.core.bean.SharedConditionEvaluator;
import summer.core.bean.SharedDependencyResolver;
import summer.core.config.ConfigBinder;
import summer.core.config.ConfigurationProperties;
import summer.core.validation.Validator;

/**
 * Builds {@link BeanContainer} for the Runtime DI engine.
 *
 * <pre>{@code
 * BeanContainer ctx = DiEngine.create(Engine.RUNTIME);
 * }</pre>
 *
 * <p>
 * For isolated builds (tests), use
 * {@code TestContainerBuilder.buildRuntime(seeds...)}.
 * </p>
 */
public final class RuntimeBeanContainerBuilder {

	private static final Logger log = LoggerFactory.getLogger(RuntimeBeanContainerBuilder.class);

	private RuntimeBeanContainerBuilder() {
	}

	/**
	 * Builds a {@link BeanContainer} from the full merged Jandex index.
	 *
	 * @return immutable bean container
	 */
	public static BeanContainer build(Object... externalBeans) {
		IndexView index = JandexIndexLoader.buildIndex();

		Set<Class<?>> componentClasses = RuntimeComponentScanner.discoverComponents(index, Scope.classpath());

		return initialize(index, componentClasses, externalBeans);
	}

	/**
	 * Builds a {@link BeanContainer} from the full merged index <b>including
	 * test-class indexes</b> ({@code jandex-test.idx}).
	 *
	 * <p>
	 * Used by the test container so {@code @Component} test fixtures in
	 * {@code src/test} are discoverable. Mirrors {@link #build(Object...)} but uses
	 * the test-aware index. Production startup must NOT use this — it keeps test
	 * classes out of the bean universe.
	 * </p>
	 *
	 * @param externalBeans
	 *            pre-instantiated beans to register
	 * @return immutable bean container
	 */
	public static BeanContainer buildFromTestIndex(Object... externalBeans) {
		ModuleIndex moduleIndex = JandexIndexLoader.buildTestModuleIndex();
		IndexView index = moduleIndex.index();
		Set<Class<?>> componentClasses = RuntimeComponentScanner.discoverComponents(moduleIndex, index,
				Scope.classpath());
		return initialize(index, componentClasses, externalBeans);
	}

	/**
	 * Builds a {@link BeanContainer} from explicit seed classes using transitive
	 * dependency expansion (exact seed scope). Called by
	 * {@link summer.test.TestContainerBuilder}.
	 *
	 * @param seeds
	 *            seed component classes
	 * @return immutable bean container
	 */
	public static BeanContainer buildFromSeeds(Class<?>... seeds) {
		return buildFromSeedsWithExternal(seeds, new Object[0]);
	}

	public static BeanContainer buildFromSeedsWithExternal(Class<?>[] seeds, Object... externalBeans) {
		IndexView index = JandexIndexLoader.buildIndex();
		// Seeds define the exact candidate set — the scope filter. Any seed that is
		// a @Component / @Configuration / @ConfigurationProperties is discovered by
		// the scanner; unannotated intentionally not force-registered.
		Set<String> seedNames = new LinkedHashSet<>();
		for (Class<?> seed : seeds) {
			seedNames.add(seed.getName());
		}
		Set<Class<?>> componentClasses = RuntimeComponentScanner.discoverComponents(index, seedNames::contains);
		return initialize(index, componentClasses, externalBeans);
	}

	/**
	 * Builds a {@link BeanContainer} from specific modules using their Jandex
	 * indexes. Only beans from the named modules are instantiated.
	 *
	 * @param moduleIndex
	 *            pre-built module index (from
	 *            {@code JandexIndexLoader.buildModuleIndex()})
	 * @param modules
	 *            module names to scope discovery to
	 * @param externalBeans
	 *            pre-instantiated beans to register
	 * @return immutable bean container
	 */
	public static BeanContainer buildFromModuleScope(ModuleIndex moduleIndex, java.util.List<String> modules,
			Object... externalBeans) {
		Set<String> deps = Set.of();
		Set<String> allInScope = new HashSet<>();
		for (String mod : modules) {
			allInScope.addAll(moduleIndex.classesInModule(mod, deps));
		}
		Scope scope = name -> allInScope.contains(name);
		IndexView index = moduleIndex.index();
		Set<Class<?>> componentClasses = RuntimeComponentScanner.discoverComponents(index, scope);
		return initialize(index, componentClasses, externalBeans);
	}

	/**
	 * Builds a {@link BeanContainer} by scanning all {@code @Component} classes
	 * under a given package tree, plus explicit seeds from outside that tree.
	 *
	 * <p>
	 * This is the right choice for integration tests: the test module's beans are
	 * discovered automatically, while infrastructure configurations that live in
	 * framework packages (e.g. {@code NettyServerConfiguration}) are passed as
	 * explicit seeds.
	 * </p>
	 *
	 * @param basePackage
	 *            package prefix for auto-scanning (e.g. {@code "summer.twitter"})
	 * @param seeds
	 *            additional seed classes (may overlap with or extend the package)
	 * @param externalBeans
	 *            pre-instantiated external beans (e.g.
	 *            {@code GlobalMiddlewareChain})
	 * @return immutable bean container
	 */
	public static BeanContainer buildModuleWithExternal(String basePackage, Class<?>[] seeds, Object... externalBeans) {
		IndexView index = JandexIndexLoader.buildIndex();
		Scope scope = Scope.packageOf(basePackage);
		Set<Class<?>> componentClasses = RuntimeComponentScanner.discoverComponents(index, scope);
		for (Class<?> seed : seeds) {
			componentClasses.add(seed);
		}
		return initialize(index, componentClasses, externalBeans);
	}

	/**
	 * Builds a {@link BeanContainer} restricted to an explicit {@link Scope}.
	 *
	 * <p>
	 * Used by {@code @SummerTest}: the scope is derived from the test class's
	 * module (plus any {@code modules}/{@code packages} it declares), so the
	 * container sees exactly the beans the test should see — no more. Discovery
	 * never loads classes outside the scope, which keeps sad-path constructors (DB
	 * connections, thread pools) of unrelated beans from firing during scan.
	 * </p>
	 *
	 * @param scope
	 *            the discovery boundary
	 * @param externalBeans
	 *            pre-instantiated beans to register (e.g. mocks)
	 * @return immutable bean container
	 */
	public static BeanContainer build(Scope scope, Object... externalBeans) {
		// Default test container: production indexes + test-class attribution
		// only (test classes are NOT in the bean universe). Unit tests scope via
		// @SummerTest(modules=...) and must not sweep in sibling test
		// fixtures. Integration tests that need fixtures call
		// build(ModuleIndex, Scope, ...) with the test-aware index instead.
		return build(JandexIndexLoader.buildModuleIndex(), scope, externalBeans);
	}

	/**
	 * Builds a {@link BeanContainer} restricted to an explicit {@link Scope}, using
	 * a caller-supplied {@link ModuleIndex}.
	 *
	 * <p>
	 * Lets the integration-test path pass a test-aware index (which includes
	 * {@code jandex-test.idx} fixtures) while the unit-test path keeps the
	 * production-only index.
	 * </p>
	 *
	 * @param moduleIndex
	 *            the module index to discover from
	 * @param scope
	 *            the discovery boundary
	 * @param externalBeans
	 *            pre-instantiated beans to register (e.g. mocks)
	 * @return immutable bean container
	 */
	public static BeanContainer build(ModuleIndex moduleIndex, Scope scope, Object... externalBeans) {
		IndexView index = moduleIndex.index();
		Set<Class<?>> componentClasses = RuntimeComponentScanner.discoverComponents(moduleIndex, index, scope);
		return initialize(index, componentClasses, externalBeans);
	}

	private static BeanContainer initialize(IndexView index, Set<Class<?>> componentClasses, Object... externalBeans) {
		ConfigBinder.setDefaultValueResolver(RuntimeDefaultValueResolver.INSTANCE);
		BeanContainer.Builder builder = new BeanContainer.Builder();

		if (externalBeans != null) {
			for (Object bean : externalBeans) {
				builder.register(bean.getClass(), bean);
				// Register under interfaces so mocks (JDK proxies) are found
				// by peek() and getBean() with their interface types.
				for (Class<?> iface : bean.getClass().getInterfaces()) {
					builder.register(iface, bean);
				}
			}
		}

		builder.register(RuntimeDiMarker.class, new RuntimeDiMarker());

		// ── Phase 1: Discovery ──────────────────────────────────────
		// Build all candidate BeanDefinitions without filtering.
		RuntimeBeanAdapter adapter = new RuntimeBeanAdapter(index);
		List<BeanDefinition> candidates = BeanDefinitionFactory.buildBeanDefinitions(componentClasses, adapter);
		candidates.add(new BeanDefinition(RuntimeDiMarker.class.getName(), "RuntimeDiMarker"));
		// Register IndexView so the dependency resolver can find it for @Bean method
		// params
		candidates.add(new BeanDefinition(IndexView.class.getName(), IndexView.class.getSimpleName()));

		// ── Phase 2: Evaluation ─────────────────────────────────────
		// Evaluate @ConditionalOnBean and @Replaces against the scoped candidate
		// set. Condition targets are expected to be reachable within the scope
		// (declared modules) plus dependency resolution — no global type-name
		// bypass, so the Runtime and AOT engines evaluate an identical set.
		new SharedConditionEvaluator().evaluate(candidates);

		// ── Phase 3: Resolution ─────────────────────────────────────
		// Bind, sort, instantiate.
		bindConfigurationProperties(componentClasses, builder);
		BeanDefinitionFactory.populateInterceptors(candidates);

		// Pre-build exception handler metadata for RuntimeExceptionHandlerRegistrar.
		// This eliminates reflection-based @ExceptionHandler scanning at registration
		// time.
		RuntimeExceptionHandlerRegistrar.setPrebuiltHandlers(candidates);

		SharedDependencyResolver resolver = new SharedDependencyResolver();
		List<BeanDefinition> sorted = resolver.resolve(candidates);

		Map<String, List<String>> interceptorMap = BeanDefinitionFactory.buildInterceptorMap(candidates);
		Map<String, Set<String>> interceptorBindingMap = new HashMap<>();
		for (BeanDefinition bd : candidates) {
			if (!bd.interceptorBindingAnnotations.isEmpty()) {
				interceptorBindingMap.put(bd.qualifiedName, bd.interceptorBindingAnnotations);
			}
		}
		BeanInstantiator instantiator = new BeanInstantiator(builder, interceptorMap, interceptorBindingMap);

		builder.register(IndexView.class, index);
		for (BeanDefinition beanDef : sorted) {
			instantiator.instantiateFromDefinition(beanDef);
		}
		// Collect route metadata from candidates for route registration
		List<RouteInfo> allRoutes = candidates.stream().flatMap(bd -> bd.routes.stream()).toList();
		builder.routes(allRoutes);

		registerRowMappers(builder, index);
		runValidators(builder);

		return builder.build(Engine.RUNTIME);
	}

	// ---- @ConfigurationProperties binding ----

	private static void bindConfigurationProperties(Set<Class<?>> componentClasses, BeanContainer.Builder builder) {
		for (Class<?> configClass : componentClasses) {
			if (!configClass.isAnnotationPresent(ConfigurationProperties.class)) {
				continue;
			}
			if (builder.peek(configClass) != null) {
				continue;
			}
			ConfigurationProperties props = configClass.getAnnotation(ConfigurationProperties.class);
			if (props == null) {
				continue;
			}
			Object instance = ConfigBinder.bind(props.prefix(), configClass);
			builder.register(configClass, instance);
			log.debug("[Summer] Bound @ConfigurationProperties: {} (prefix='{}')", configClass.getSimpleName(),
					props.prefix());
		}
	}

	// ---- Infrastructure ----

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerRowMappers(BeanContainer.Builder builder, IndexView index) {
		try {
			Class<?> factoryClass = Class.forName("summer.data.jdbc.RowMapperFactory");
			var metas = (java.util.List<?>) factoryClass.getMethod("scanJandex", IndexView.class).invoke(null, index);
			if (metas.isEmpty()) {
				return;
			}

			Class<?> jdbcTemplateClass;
			try {
				jdbcTemplateClass = Class.forName("summer.data.jdbc.JdbcTemplate");
			} catch (ClassNotFoundException e) {
				return;
			}

			Object jdbcTemplate = builder.peek(jdbcTemplateClass);
			if (jdbcTemplate == null) {
				return;
			}

			java.lang.reflect.Method registerMethod = jdbcTemplateClass.getMethod("registerMapper", Class.class,
					Class.forName("summer.data.jdbc.RowMapper"));

			for (Object meta : metas) {
				try {
					String modelClassName = (String) meta.getClass().getMethod("modelClassName").invoke(meta);
					Class<?> modelClass = Class.forName(modelClassName);
					Object mapper = factoryClass.getMethod("createReflective", Class.class, meta.getClass())
							.invoke(null, modelClass, meta);
					registerMethod.invoke(jdbcTemplate, modelClass, mapper);
				} catch (ClassNotFoundException e) {
					log.debug("[Summer] Could not load @RowModel class", e);
				}
			}
		} catch (ClassNotFoundException e) {
			// summer-data-jdbc not on classpath — nothing to do
		} catch (Exception e) {
			log.debug("[Summer] Failed to register RowMapper registry: {}", e.getMessage());
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
