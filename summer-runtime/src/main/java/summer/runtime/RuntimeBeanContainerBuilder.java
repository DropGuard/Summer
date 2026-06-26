package summer.runtime;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.BeanContainer;
import summer.core.BeanRegistry;
import summer.core.Component;
import summer.core.DiEngine;
import summer.core.Engine;
import summer.core.ErrorCode;
import summer.core.RuntimeDiMarker;
import summer.core.bean.BeanDefinition;
import summer.core.bean.SharedConditionEvaluator;
import summer.core.bean.SharedDependencyResolver;
import summer.core.config.ConfigBinder;
import summer.core.config.ConfigurationProperties;
import summer.core.exception.BeanCreationException;
import summer.core.validation.Validator;

/**
 * Builds {@link BeanContainer} for the Runtime DI engine.
 *
 * <pre>{@code
 * BeanContainer ctx = RuntimeBeanContainerBuilder.build(); // full scan
 * BeanContainer ctx = RuntimeBeanContainerBuilder.buildScoped(TestClass.class, InfraConfig.class); // isolated
 * BeanContainer ctx = RuntimeBeanContainerBuilder.buildFromSeeds(A.class, B.class); // explicit seeds
 * }</pre>
 */
public final class RuntimeBeanContainerBuilder implements DiEngine {

	private static final Logger log = LoggerFactory.getLogger(RuntimeBeanContainerBuilder.class);

	private static final DotName CONFIGURATION_PROPERTIES = DotName.createSimple(ConfigurationProperties.class);

	public RuntimeBeanContainerBuilder() {
	}

	@Override
	public BeanContainer create() throws Exception {
		return build();
	}

	/**
	 * Builds a {@link BeanContainer} via full Jandex classpath scanning.
	 *
	 * @return immutable bean container
	 */
	public static BeanContainer build() {
		IndexView index = JandexIndexLoader.buildIndex();

		Set<Class<?>> componentClasses = RuntimeComponentScanner.discoverComponents(index);
		for (AnnotationInstance ann : index.getAnnotations(ConfigurationProperties.class)) {
			ClassInfo ci = ann.target().asClass();
			if (ci.isInterface() || ci.isAbstract()) {
				continue;
			}
			try {
				componentClasses.add(Class.forName(ci.name().toString()));
			} catch (ClassNotFoundException e) {
				log.debug("[Summer] Could not load @ConfigurationProperties class: {}", ci.name());
			}
		}

		return initialize(index, componentClasses);
	}

	/**
	 * Builds a scoped {@link BeanContainer} by discovering {@code @Component} inner
	 * classes of the given test class, combined with additional seed classes. Uses
	 * transitive dependency expansion (no full classpath scanning).
	 *
	 * <pre>{@code
	 * // Scanning behavior test: inner classes auto-discovered
	 * buildScoped(HttpMiddlewareIntegrationTest.class, NettyServerConfiguration.class, RouterConfiguration.class);
	 * }</pre>
	 *
	 * @param testClass
	 *            test class whose inner {@code @Component} classes are
	 *            auto-discovered
	 * @param additionalSeeds
	 *            extra seed component classes (e.g. infrastructure configs)
	 * @return immutable bean container
	 */
	public static BeanContainer buildScoped(Class<?> testClass, Class<?>... additionalSeeds) {
		List<Class<?>> allSeeds = new ArrayList<>(List.of(discoverInnerComponents(testClass)));
		allSeeds.addAll(List.of(additionalSeeds));
		if (allSeeds.isEmpty()) {
			throw new BeanCreationException(ErrorCode.BEAN_CREATION_FAILED,
					"No @Component inner classes found in " + testClass.getSimpleName()
							+ ". Add inner static classes annotated with @Component or its meta-annotations.");
		}
		return buildFromSeeds(allSeeds.toArray(new Class<?>[0]));
	}

	/**
	 * Builds a {@link BeanContainer} from explicit seed classes using transitive
	 * dependency expansion (no full classpath scanning).
	 *
	 * <pre>{@code
	 * // TCK test: explicit seeds
	 * buildFromSeeds(CircularA.class, CircularB.class);
	 * }</pre>
	 *
	 * @param seeds
	 *            seed component classes
	 * @return immutable bean container
	 */
	public static BeanContainer buildFromSeeds(Class<?>... seeds) {
		IndexView index = JandexIndexLoader.buildIndex();
		Set<Class<?>> componentClasses = RuntimeComponentScanner.transitiveExpand(new LinkedHashSet<>(List.of(seeds)),
				index);
		return initialize(index, componentClasses);
	}

	private static Class<?>[] discoverInnerComponents(Class<?> testClass) {
		List<Class<?>> components = new ArrayList<>();
		for (Class<?> inner : testClass.getDeclaredClasses()) {
			if (isComponentOrMeta(inner)) {
				components.add(inner);
			}
		}
		return components.toArray(new Class<?>[0]);
	}

	private static boolean isComponentOrMeta(Class<?> clazz) {
		// Direct @Component
		if (clazz.isAnnotationPresent(Component.class)) {
			return true;
		}
		// Meta-annotations: @GlobalMiddleware → @Component,
		// @RestController → @Component, @Configuration → @Component
		for (Annotation ann : clazz.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(Component.class)) {
				return true;
			}
		}
		return false;
	}

	private static BeanContainer initialize(IndexView index, Set<Class<?>> componentClasses) {
		ConfigBinder.setDefaultValueResolver(RuntimeDefaultValueResolver.INSTANCE);

		BeanRegistry registry = new BeanRegistry();
		registry.registerSingleton(RuntimeDiMarker.class, new RuntimeDiMarker());

		bindConfigurationProperties(componentClasses, registry);

		RuntimeBeanAdapter adapter = new RuntimeBeanAdapter(index);
		List<BeanDefinition> allBeans = BeanDefinitionFactory.buildBeanDefinitions(componentClasses, registry, adapter);

		// RuntimeDiMarker is not @Component — register explicitly for
		// @ConditionalOnBean
		allBeans.add(new BeanDefinition(RuntimeDiMarker.class.getName(), "RuntimeDiMarker"));

		SharedConditionEvaluator conditionEvaluator = new SharedConditionEvaluator(index);
		conditionEvaluator.evaluate(allBeans);

		BeanDefinitionFactory.populateInterceptors(allBeans);

		SharedDependencyResolver resolver = new SharedDependencyResolver();
		List<BeanDefinition> sortedBeans = resolver.resolve(allBeans);

		Map<String, List<String>> interceptorMap = BeanDefinitionFactory.buildInterceptorMap(allBeans);
		BeanInstantiator instantiator = new BeanInstantiator(registry, interceptorMap);
		for (BeanDefinition beanDef : sortedBeans) {
			instantiator.instantiateFromDefinition(beanDef);
		}

		registerRowMappers(registry, index);
		runValidators(registry);

		return BeanContainer.create(registry, Engine.RUNTIME);
	}

	// ---- @ConfigurationProperties binding ----

	private static void bindConfigurationProperties(Set<Class<?>> componentClasses, BeanRegistry registry) {
		for (Class<?> configClass : componentClasses) {
			if (!configClass.isAnnotationPresent(ConfigurationProperties.class)) {
				continue;
			}
			if (registry.peek(configClass) != null) {
				continue;
			}
			ConfigurationProperties props = configClass.getAnnotation(ConfigurationProperties.class);
			if (props == null) {
				continue;
			}
			Object instance = ConfigBinder.bind(props.prefix(), configClass);
			registry.registerSingleton(configClass, instance);
			log.debug("[Summer] Bound @ConfigurationProperties: {} (prefix='{}')", configClass.getSimpleName(),
					props.prefix());
		}
	}

	// ---- Infrastructure ----

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerRowMappers(BeanRegistry registry, IndexView index) {
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

			Object jdbcTemplate = registry.peek(jdbcTemplateClass);
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
	private static void runValidators(BeanRegistry registry) {
		for (Object bean : registry.singletons().values()) {
			if (bean instanceof Validator<?> validator) {
				Class<?> targetType = validator.targetType();
				Object target = registry.peek(targetType);
				if (target != null) {
					((Validator<Object>) validator).validate(target);
				}
			}
		}
	}
}
