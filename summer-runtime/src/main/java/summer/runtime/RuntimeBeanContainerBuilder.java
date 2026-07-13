package summer.runtime;

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
	 * Builds a {@link BeanContainer} via full Jandex classpath scanning.
	 *
	 * @return immutable bean container
	 */
	public static BeanContainer build(Object... externalBeans) {
		IndexView index = JandexIndexLoader.buildIndex();

		Set<Class<?>> componentClasses = RuntimeComponentScanner.discoverComponents(index, Scope.classpath());

		return initialize(index, componentClasses, externalBeans);
	}

	/**
	 * Builds a {@link BeanContainer} from explicit seed classes using transitive
	 * dependency expansion (no full classpath scanning). Called by
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
		Set<String> seedNames = new LinkedHashSet<>();
		for (Class<?> seed : seeds) {
			seedNames.add(seed.getName());
		}
		Scope scope = Scope.reachableFrom(seedNames, index);
		Set<Class<?>> componentClasses = RuntimeComponentScanner.discoverComponents(index, scope);
		// Seeds are an authoritative input — always included regardless of index
		// coverage.
		// The index drives BFS discovery and component scanning;
		// seeds represent the caller's explicit declaration of intent.
		for (Class<?> seed : seeds) {
			componentClasses.add(seed);
		}
		return initialize(index, componentClasses, externalBeans);
	}

	private static BeanContainer initialize(IndexView index, Set<Class<?>> componentClasses, Object... externalBeans) {
		ConfigBinder.setDefaultValueResolver(RuntimeDefaultValueResolver.INSTANCE);
		BeanContainer.Builder builder = new BeanContainer.Builder();

		if (externalBeans != null) {
			for (Object bean : externalBeans) {
				builder.register(bean.getClass(), bean);
			}
		}

		builder.register(RuntimeDiMarker.class, new RuntimeDiMarker());

		// ── Phase 1: Discovery ──────────────────────────────────────
		// Build all candidate BeanDefinitions without filtering.
		RuntimeBeanAdapter adapter = new RuntimeBeanAdapter(index);
		List<BeanDefinition> candidates = BeanDefinitionFactory.buildBeanDefinitions(componentClasses, adapter);
			candidates.add(new BeanDefinition(RuntimeDiMarker.class.getName(), "RuntimeDiMarker"));
			// Register IndexView so the dependency resolver can find it for @Bean method params
			candidates.add(new BeanDefinition(IndexView.class.getName(), IndexView.class.getSimpleName()));

		// ── Phase 2: Evaluation ─────────────────────────────────────
		// Evaluate @ConditionalOnBean and @Replaces against the candidate set.
		SharedConditionEvaluator evaluator = new SharedConditionEvaluator(index);
		evaluator.evaluate(candidates);

		// ── Phase 3: Resolution ─────────────────────────────────────
		// Bind, sort, instantiate.
		bindConfigurationProperties(componentClasses, builder);
		BeanDefinitionFactory.populateInterceptors(candidates);

		SharedDependencyResolver resolver = new SharedDependencyResolver();
		List<BeanDefinition> sorted = resolver.resolve(candidates);

		Map<String, List<String>> interceptorMap = BeanDefinitionFactory.buildInterceptorMap(candidates);
		BeanInstantiator instantiator = new BeanInstantiator(builder, interceptorMap);
		builder.register(IndexView.class, index);
		for (BeanDefinition beanDef : sorted) {
			instantiator.instantiateFromDefinition(beanDef);
		}

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
