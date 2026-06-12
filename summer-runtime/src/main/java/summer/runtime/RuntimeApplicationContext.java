package summer.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ApplicationContext;
import summer.core.Component;
import summer.core.Engine;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.config.ConfigBinder;
import summer.core.config.ConfigurationProperties;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.BeanCreationException;
import summer.core.exception.CircularDependencyException;
import summer.core.ErrorCode;
import summer.core.exception.NoSuchBeanException;

/**
 * The runtime Summer application context that manages beans and their
 * dependencies using Jandex and Reflection.
 */
public class RuntimeApplicationContext implements ApplicationContext {

	private static final Logger log = LoggerFactory.getLogger(RuntimeApplicationContext.class);

	private static final DotName COMPONENT = DotName.createSimple(Component.class);
	private static final DotName CONFIG_PROPERTIES = DotName.createSimple(ConfigurationProperties.class);

	private final Set<Class<?>> componentClasses = new HashSet<>();
	private IndexView lastIndex;
	private final DependencyGraph dependencyGraph;
	private final Map<Class<?>, Object> singletons = new java.util.LinkedHashMap<>();
	private final List<AutoCloseable> closeables = new ArrayList<>();
	private List<Object> instantiationOrder = List.of();
	private RuntimeBeanFactory beanFactory;

	public RuntimeApplicationContext() {
		this.dependencyGraph = new DependencyGraph();
		ConfigBinder.setDefaultValueResolver(RuntimeDefaultValueResolver.INSTANCE);
		registerSingleton(summer.core.RuntimeDiMarker.class, new summer.core.RuntimeDiMarker());
	}

	/**
	 * Convenience factory method that creates a RuntimeApplicationContext and scans
	 * from the entry point's package.
	 *
	 * <p>
	 * Registers {@link RuntimeDiMarker} as a singleton before scanning, enabling
	 * {@code @ConditionalOnBean(RuntimeDiMarker.class)} on runtime-specific
	 * configurations.
	 * </p>
	 */
	public static RuntimeApplicationContext create(Class<?> entryPoint) {
		ConfigBinder.setDefaultValueResolver(RuntimeDefaultValueResolver.INSTANCE);
		RuntimeApplicationContext ctx = new RuntimeApplicationContext();
		ctx.registerSingleton(summer.core.RuntimeDiMarker.class, new summer.core.RuntimeDiMarker());
		ctx.scan();
		ctx.initializeBeans();
		return ctx;
	}

	@Override
	public Engine engine() {
		return Engine.RUNTIME;
	}

	// ── Component Discovery ──────────────────────────────────────────

	public void scan() {
		IndexView index = JandexIndexLoader.buildIndex();
		this.lastIndex = index;
		for (ClassInfo classInfo : index.getKnownClasses()) {
			if (classInfo.isInterface() || classInfo.isAbstract())
				continue;

			String className = classInfo.name().toString();
			if (className.contains(".config.generated.") || className.contains("$Generated"))
				continue;

			if (hasMetaComponentAnnotation(classInfo, new HashSet<>())) {
				try {
					Class<?> clazz = Class.forName(className);
					log.debug("[Summer] Registering component: {} isInterface={}", className, clazz.isInterface());
					componentClasses.add(clazz);
				} catch (ClassNotFoundException e) {
					log.debug("[Summer] Could not load indexed class: {}", classInfo.name());
				}
			}
		}
		log.debug("[Summer] Registered {} component classes", componentClasses.size());
	}

	public void registerComponent(Class<?> clazz) {
		log.debug("[Summer] registerComponent called: {} isInterface={}", clazz.getName(), clazz.isInterface());
		if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
			if (isComponent(clazz)) {
				throw new BeanCreationException(ErrorCode.BEAN_CREATION_FAILED,
						"@Component cannot be placed on an interface or abstract class: " + clazz.getName()
								+ ". Annotate the concrete implementation instead.");
			}
			return;
		}
		if (!isComponent(clazz) && !clazz.isAnnotationPresent(ConfigurationProperties.class)) {
			throw new BeanCreationException(ErrorCode.BEAN_CREATION_FAILED,
					"Class " + clazz.getName() + " is not annotated with @Component or @ConfigurationProperties");
		}
		componentClasses.add(clazz);
	}

	public void applyProfile(Set<Class<?>> enabledBeans) {
		if (enabledBeans != null && !enabledBeans.isEmpty()) {
			int before = componentClasses.size();
			componentClasses.retainAll(enabledBeans);
			log.debug("[Summer] Profile filter: {} -> {} component classes", before, componentClasses.size());
		}
	}

	public org.jboss.jandex.IndexView getIndex() {
		return lastIndex;
	}

	private List<Class<?>> discoverConfigurationProperties() {
		if (lastIndex == null) {
			return List.of();
		}
		List<Class<?>> result = new ArrayList<>();
		for (AnnotationInstance ann : lastIndex.getAnnotations(CONFIG_PROPERTIES)) {
			ClassInfo ci = ann.target().asClass();
			if (ci.isInterface() || ci.isAbstract())
				continue;
			try {
				result.add(Class.forName(ci.name().toString()));
			} catch (ClassNotFoundException e) {
				log.debug("[Summer] Could not load @ConfigurationProperties class: {}", ci.name());
			}
		}
		return result;
	}

	private boolean hasMetaComponentAnnotation(ClassInfo classInfo, Set<DotName> visited) {
		if (classInfo == null)
			return false;
		DotName name = classInfo.name();
		if (!visited.add(name))
			return false;

		if (classInfo.hasAnnotation(COMPONENT))
			return true;

		for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
			if (hasMetaComponentAnnotation(lastIndex.getClassByName(ann.name()), visited))
				return true;
		}
		return false;
	}

	private boolean isComponent(Class<?> clazz) {
		if (clazz.isAnnotationPresent(Component.class))
			return true;
		for (java.lang.annotation.Annotation ann : clazz.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(Component.class))
				return true;
		}
		return false;
	}

	// ── Bean Lifecycle ───────────────────────────────────────────────

	public void registerSingleton(Class<?> type, Object instance) {
		singletons.put(type, instance);
	}

	public void applyConfigOverrides(Map<String, String> overrides) {
		if (overrides != null && !overrides.isEmpty()) {
			for (Map.Entry<String, String> entry : overrides.entrySet()) {
				System.setProperty(entry.getKey(), entry.getValue());
			}
			log.debug("[Summer] Applied {} config override(s)", overrides.size());
		}
	}

	public void initializeBeans() {
		beanFactory = new RuntimeBeanFactory(singletons, closeables, dependencyGraph, this);

		bindConfigurationProperties();

		Set<Object> allNodes = new java.util.LinkedHashSet<>(componentClasses);

		// Include programmatically registered singletons in conditional evaluation
		allNodes.addAll(singletons.keySet());

		for (Class<?> clazz : componentClasses) {
			if (clazz.isAnnotationPresent(Configuration.class)) {
				for (Method method : clazz.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Bean.class)) {
						allNodes.add(method);
					}
				}
			}
		}

		// 1. Evaluate @ConditionalOnBean and @Replaces
		RuntimeConditionEvaluator.evaluate(allNodes);
		componentClasses.removeIf(clazz -> !allNodes.contains(clazz));

		// 2. Build Dependency Graph
		dependencyGraph.buildGraph(allNodes);

		if (dependencyGraph.hasCircularDependencies()) {
			throw new CircularDependencyException("Circular dependencies detected");
		}

		// 4. Topological Sort and Instantiation
		instantiationOrder = dependencyGraph.topologicalSort();

		for (Object node : instantiationOrder) {
			if (node instanceof Class<?> clazz) {
				beanFactory.instantiateBean(clazz);
			} else if (node instanceof Method method) {
				beanFactory.invokeBeanProducer(method);
			}
		}

		// 5. Validation Phase
		runValidators();
	}

	/**
	 * Scans for {@code @ConfigurationProperties}-annotated records and binds them
	 * from {@code application.yml}. Results are registered as singletons so they
	 * are available as dependencies for other beans.
	 *
	 * <p>
	 * Fields absent from YAML are left as {@code null}. Skips types already
	 * registered (e.g. via a manual {@code @Bean} method).
	 * </p>
	 */
	private void bindConfigurationProperties() {
		List<Class<?>> configClasses = discoverConfigurationProperties();
		if (configClasses.isEmpty()) {
			return;
		}

		for (Class<?> configClass : configClasses) {
			if (singletons.containsKey(configClass)) {
				continue; // already registered (e.g. by @Bean)
			}
			ConfigurationProperties ann = configClass.getAnnotation(ConfigurationProperties.class);
			if (ann == null)
				continue;
			String prefix = ann.prefix();
			Object instance = ConfigBinder.bind(prefix, configClass);
			singletons.put(configClass, instance);
			log.debug("[Summer] Bound @ConfigurationProperties: {} (prefix='{}')", configClass.getSimpleName(), prefix);
		}
	}

	@SuppressWarnings("unchecked")
	private void runValidators() {
		for (Object bean : singletons.values()) {
			if (bean instanceof summer.core.validation.Validator<?> validator) {
				Class<?> targetType = validator.targetType();
				Object target = singletons.get(targetType);
				if (target != null) {
					((summer.core.validation.Validator<Object>) validator).validate(target);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T getBean(Class<T> type) {
		Object instance = singletons.get(type);
		if (instance != null) {
			return (T) instance;
		}

		List<Object> matches = new ArrayList<>();
		for (Object singleton : singletons.values()) {
			if (type.isInstance(singleton) && !matches.contains(singleton)) {
				matches.add(singleton);
			}
		}

		if (matches.isEmpty()) {
			throw new NoSuchBeanException("No bean found of type: " + type.getName());
		}
		if (matches.size() == 1) {
			return (T) matches.get(0);
		}
		throw new AmbiguousBeanException("Ambiguous dependency. Multiple beans found for type: " + type.getName());
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> getBeans(Class<T> type) {
		List<T> result = new ArrayList<>();
		for (Object instance : singletons.values()) {
			if (instance != null && type.isInstance(instance) && !result.contains(instance)) {
				result.add((T) instance);
			}
		}
		return result;
	}

	public boolean containsBean(Class<?> type) {
		if (singletons.containsKey(type)) {
			return true;
		}
		for (Object instance : singletons.values()) {
			if (instance != null && type.isInstance(instance)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Set<Class<?>> getRegisteredTypes() {
		return Collections.unmodifiableSet(componentClasses);
	}

	@Override
	public void close() {
		for (AutoCloseable closeable : closeables.reversed()) {
			try {
				closeable.close();
				log.debug("[Summer] Closed: {}", closeable.getClass().getSimpleName());
			} catch (Exception e) {
				log.warn("[Summer] Error closing resource: {}", closeable.getClass().getSimpleName(), e);
			}
		}
	}
}
