package summer.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
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
import summer.aop.MethodInterceptor;
import summer.core.BeanContainer;
import summer.core.BeanRegistry;
import summer.core.Component;
import summer.core.Engine;
import summer.core.ErrorCode;
import summer.core.Provider;
import summer.core.RuntimeDiMarker;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Replaces;
import summer.core.config.ConfigBinder;
import summer.core.config.ConfigurationProperties;
import summer.core.exception.BeanCreationException;
import summer.core.exception.CircularDependencyException;
import summer.core.exception.NoSuchBeanException;
import summer.core.validation.Validator;

/**
 * Runtime (reflection-based) DI engine entry point. Discovers beans from the
 * Jandex index, evaluates conditions, builds the dependency graph, instantiates
 * beans (with AOP proxy wrapping), and produces an immutable
 * {@link BeanContainer}.
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>{@code
 * // default: scan classpath + initialize
 * BeanContainer container = RuntimeApplicationContext.create();
 *
 * // with extra components (e.g. test fixtures)
 * BeanContainer container = RuntimeApplicationContext.builder()
 *         .registerComponent(MyConfig.class)
 *         .build();
 * }</pre>
 */
public final class RuntimeApplicationContext {

    private static final Logger log = LoggerFactory.getLogger(RuntimeApplicationContext.class);

    private static final DotName COMPONENT = DotName.createSimple(Component.class);
    private static final DotName CONFIGURATION_PROPERTIES = DotName.createSimple(ConfigurationProperties.class);

    private RuntimeApplicationContext() {
    }

    /**
     * Creates a {@link BeanContainer} with auto-detection: AOT if the
     * generated context is on the classpath, otherwise falls back to
     * runtime scanning.
     */
    public static BeanContainer create() {
        try {
            return create(summer.core.Engine.AOT);
        } catch (Exception e) {
            log.debug("No AOT context, falling back to runtime: {}", e.getMessage());
            return createRuntime();
        }
    }

    /**
     * Creates a {@link BeanContainer} for the given engine.
     * {@link summer.core.Engine#AOT} throws if no AOT context is on the
     * classpath; {@link summer.core.Engine#RUNTIME} always scans.
     */
    public static BeanContainer create(summer.core.Engine engine) {
        return switch (engine) {
            case AOT -> {
                try {
                    Class<?> aotClass = Class.forName("summer.core.aot.GeneratedAotContext");
                    java.lang.reflect.Method createMethod = aotClass.getMethod("create");
                    yield (BeanContainer) createMethod.invoke(null);
                } catch (Exception e) {
                    throw new summer.core.exception.BeanCreationException(
                            summer.core.ErrorCode.BEAN_CREATION_FAILED,
                            "AOT context not found. Run summer-maven-plugin before building.", e);
                }
            }
            case RUNTIME -> createRuntime();
        };
    }

    /**
     * Creates a {@link BeanContainer} using runtime Jandex classpath
     * scanning — the pure runtime path. No AOT detection.
     */
    public static BeanContainer createRuntime() {
        return builder().build();
    }
    /**
     * Builds a {@link BeanContainer} containing only the given beans and their
     * transitive dependency closure. No Jandex classpath scanning is performed.
     * This is the "local expansion" entry point for isolated unit tests.
     */
    public static BeanContainer containing(Class<?>... components) {
        return new Builder().buildLocal(components);
    }

    /**
     * Returns a builder for customizing container creation. Use this when you
     * need to register extra components (e.g. test fixtures) or apply profile
     * filters in addition to Jandex discovery.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BeanContainer}. Supports extra component registration,
     * profile filtering, and config overrides.
     */
    public static final class Builder {

        private final Set<Class<?>> extraComponents = new LinkedHashSet<>();
        private Set<Class<?>> enabledBeans;
        private Map<String, String> configOverrides;

        private Builder() {
        }

        /**
         * Registers an additional component class. The class must be annotated
         * with {@link Component} (or a meta-annotation), {@link Configuration},
         * or {@link ConfigurationProperties}.
         */
        public Builder registerComponent(Class<?> clazz) {
            extraComponents.add(clazz);
            return this;
        }

        /**
         * Restricts the active beans to the given set. Empty or null means no
         * filtering.
         */
        public Builder withEnabledBeans(Set<Class<?>> enabledBeans) {
            this.enabledBeans = enabledBeans;
            return this;
        }

        /**
         * Applies the given config overrides as system properties before bean
         * binding. Useful for testing {@code @ConfigurationProperties}.
         */
        public Builder withConfigOverrides(Map<String, String> overrides) {
            this.configOverrides = overrides;
            return this;
        }

        /**
         * Builds the {@link BeanContainer}.
         *
         * <p>
         * Always performs full Jandex classpath scanning. Any components
         * registered via {@link #registerComponent(Class)} are added on top
         * of the scan result (e.g. mock configurations for integration tests).
         * Use {@link RuntimeApplicationContext#containing(Class...)} for the
         * local-expansion (test isolation) path.
         * </p>
         */
        public BeanContainer build() {
            ConfigBinder.setDefaultValueResolver(RuntimeDefaultValueResolver.INSTANCE);

            if (configOverrides != null && !configOverrides.isEmpty()) {
                for (Map.Entry<String, String> entry : configOverrides.entrySet()) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            }

            BeanRegistry registry = new BeanRegistry();
            registry.registerSingleton(RuntimeDiMarker.class, new RuntimeDiMarker());

            IndexView index = JandexIndexLoader.buildIndex();

            // Full classpath scan (production / integration test mode)
            Set<Class<?>> componentClasses = discoverComponents(index);
            // Also include @ConfigurationProperties beans that are not
            // @Component (e.g. PageableProperties).
            for (AnnotationInstance ann : index.getAnnotations(CONFIGURATION_PROPERTIES)) {
                ClassInfo ci = ann.target().asClass();
                if (ci.isInterface() || ci.isAbstract()) {
                    continue;
                }
                try {
                    Class<?> configClass = Class.forName(ci.name().toString());
                    componentClasses.add(configClass);
                } catch (ClassNotFoundException e) {
                    log.debug("[Summer] Could not load @ConfigurationProperties class: {}", ci.name());
                }
            }
            // Add explicitly registered extra components
            for (Class<?> clazz : extraComponents) {
                validateExtraComponent(clazz);
                componentClasses.add(clazz);
            }

            // Apply profile filter
            if (enabledBeans != null && !enabledBeans.isEmpty()) {
                componentClasses.retainAll(enabledBeans);
            }

            // Pre-bind @ConfigurationProperties (before condition eval so they count as available)
            bindConfigurationProperties(componentClasses, index, registry);

            // Build the full node set (component classes + @Bean methods + programmatic singletons)
            Set<Object> allNodes = new LinkedHashSet<>(componentClasses);
            allNodes.addAll(registry.singletons().keySet());
            for (Class<?> clazz : componentClasses) {
                if (clazz.isAnnotationPresent(Configuration.class)) {
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(Bean.class)) {
                            allNodes.add(method);
                        }
                    }
                }
            }

            // Phase 1: condition evaluation (@ConditionalOnBean + @Replaces)
            ConditionEvaluator.evaluate(allNodes);
            componentClasses.retainAll(allNodes.stream().filter(n -> n instanceof Class<?>).map(n -> (Class<?>) n)
                    .collect(java.util.stream.Collectors.toSet()));

            // Phase 2: build dependency graph
            DependencyGraph dependencyGraph = new DependencyGraph();
            dependencyGraph.buildGraph(allNodes);
            if (dependencyGraph.hasCircularDependencies()) {
                throw new CircularDependencyException("Circular dependencies detected");
            }

            // Phase 3: instantiate in topological order
            List<Object> instantiationOrder = dependencyGraph.topologicalSort();
            BeanInstantiator instantiator = new BeanInstantiator(registry, dependencyGraph);
            for (Object node : instantiationOrder) {
                if (node instanceof Class<?> clazz) {
                    instantiator.instantiateBean(clazz);
                } else if (node instanceof Method method) {
                    instantiator.invokeBeanProducer(method);
                }
            }

            // Register @RowModel reflective mappers with JdbcTemplate
            registerRowMappers(registry, index);

            // Phase 4: validation
            runValidators(registry);

            return BeanContainer.create(registry, Engine.RUNTIME);
        }

        /**
         * Transitive dependency closure starting from the given seed classes.
         * Uses the Jandex index to resolve interface/abstract dependencies to
         * their concrete implementations.
         */
        private Set<Class<?>> transitiveExpand(Set<Class<?>> seeds, IndexView index) {
            // Validate all seeds first
            for (Class<?> clazz : seeds) {
                validateExtraComponent(clazz);
            }

            Set<Class<?>> closure = new LinkedHashSet<>(seeds);
            Deque<Class<?>> queue = new ArrayDeque<>(seeds);

            while (!queue.isEmpty()) {
                Class<?> current = queue.pollFirst();

                // @Replaces target: the replaced class must be in the closure
                // so that ConditionEvaluator can find and redirect it.
                // If the target is an interface/abstract, also pull in its
                // known implementations so they can be replaced.
                Replaces replaces = current.getAnnotation(Replaces.class);
                if (replaces != null) {
                    Class<?> target = replaces.value();
                    if (closure.add(target)) {
                        queue.addLast(target);
                    }
                    for (Class<?> impl : findImplementations(target, index)) {
                        if (closure.add(impl)) {
                            queue.addLast(impl);
                        }
                    }
                }

                // @ConfigurationProperties have no constructor dependencies
                if (current.isAnnotationPresent(ConfigurationProperties.class)) {
                    continue;
                }

                // @Configuration: also pull in @Bean method return types
                if (current.isAnnotationPresent(Configuration.class)) {
                    for (Method method : current.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(Bean.class)) {
                            Class<?> returnType = method.getReturnType();
                            if (!closure.contains(returnType) && !returnType.isInterface()) {
                                try {
                                    Class.forName(returnType.getName());
                                } catch (ClassNotFoundException e) {
                                    log.debug("[Summer] Could not load @Bean return type: {}", returnType.getName());
                                }
                            }
                            // Also check method-level @Replaces
                            Replaces beanReplaces = method.getAnnotation(Replaces.class);
                            if (beanReplaces != null) {
                                Class<?> beanTarget = beanReplaces.value();
                                if (closure.add(beanTarget)) {
                                    queue.addLast(beanTarget);
                                }
                                for (Class<?> impl : findImplementations(beanTarget, index)) {
                                    if (closure.add(impl)) {
                                        queue.addLast(impl);
                                    }
                                }
                            }
                        }
                    }
                }

                // Examine constructor parameters for dependencies
                Constructor<?>[] constructors = current.getConstructors();
                if (constructors.length != 1) {
                    continue; // Let DependencyGraph.validateConstructor handle this
                }
                Constructor<?> ctor = constructors[0];
                for (Class<?> paramType : ctor.getParameterTypes()) {
                    if (paramType == BeanContainer.class) {
                        continue;
                    }

                    // Discover implementations from the Jandex index
                    List<Class<?>> impls = findImplementations(paramType, index);
                    for (Class<?> impl : impls) {
                        if (closure.add(impl)) {
                            queue.addLast(impl);
                        }
                    }
                }
            }

            log.debug("[Summer] Transitive expansion: {} seeds -> {} closure beans", seeds.size(), closure.size());
            return closure;
        }

        /**
         * Finds concrete implementations of a dependency type from the Jandex
         * index. If the type is already a concrete class, returns it directly.
         * If it's an interface or abstract class, returns all known
         * implementors that are annotated with {@code @Component} (or a
         * meta-annotation).
         */
        private List<Class<?>> findImplementations(Class<?> type, IndexView index) {
            if (!type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
                // Concrete class — can be used directly
                return List.of(type);
            }

            List<Class<?>> result = new ArrayList<>();
            DotName dotName = DotName.createSimple(type.getName());
            for (ClassInfo ci : index.getKnownDirectImplementors(dotName)) {
                if (ci.isInterface() || ci.isAbstract()) {
                    continue;
                }
                if (hasMetaComponentAnnotation(ci, index, new HashSet<>())) {
                    try {
                        result.add(Class.forName(ci.name().toString()));
                    } catch (ClassNotFoundException e) {
                        log.debug("[Summer] Could not load implementation: {}", ci.name());
                    }
                }
            }
            return result;
        }

        // ---- Infrastructure ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static void registerRowMappers(BeanRegistry registry, IndexView index) {
            try {
                Class<?> factoryClass = Class.forName("summer.data.jdbc.RowMapperFactory");
                var metas = (java.util.List<?>) factoryClass.getMethod("scanJandex", IndexView.class)
                        .invoke(null, index);
                if (metas.isEmpty()) {
                    return;
                }

                // JdbcTemplate may not be on classpath — guard with Class.forName
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

                java.lang.reflect.Method registerMethod = jdbcTemplateClass.getMethod(
                        "registerMapper", Class.class,
                        Class.forName("summer.data.jdbc.RowMapper"));

                for (Object meta : metas) {
                    try {
                        String modelClassName = (String) meta.getClass().getMethod("modelClassName")
                                .invoke(meta);
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

        // ---- Discovery ----

        private Set<Class<?>> discoverComponents(IndexView index) {
            Set<Class<?>> componentClasses = new LinkedHashSet<>();
            for (ClassInfo classInfo : index.getKnownClasses()) {
                if (classInfo.isInterface() || classInfo.isAbstract()) {
                    continue;
                }
                String className = classInfo.name().toString();
                if (className.contains(".config.generated.") || className.contains("$Generated")) {
                    continue;
                }
                if (hasMetaComponentAnnotation(classInfo, index, new HashSet<>())) {
                    try {
                        Class<?> clazz = Class.forName(className);
                        componentClasses.add(clazz);
                    } catch (ClassNotFoundException e) {
                        log.debug("[Summer] Could not load indexed class: {}", classInfo.name());
                    }
                }
            }
            log.debug("[Summer] Discovered {} component classes", componentClasses.size());
            return componentClasses;
        }

        private void validateExtraComponent(Class<?> clazz) {
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
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
        }

        private boolean hasMetaComponentAnnotation(ClassInfo classInfo, IndexView index, Set<DotName> visited) {
            if (classInfo == null) {
                return false;
            }
            DotName name = classInfo.name();
            if (!visited.add(name)) {
                return false;
            }
            if (classInfo.hasAnnotation(COMPONENT)) {
                return true;
            }
            for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
                if (hasMetaComponentAnnotation(index.getClassByName(ann.name()), index, visited)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isComponent(Class<?> clazz) {
            if (clazz.isAnnotationPresent(Component.class)) {
                return true;
            }
            for (java.lang.annotation.Annotation ann : clazz.getAnnotations()) {
                if (ann.annotationType().isAnnotationPresent(Component.class)) {
                    return true;
                }
            }
            return false;
        }

        // ---- @ConfigurationProperties binding ----

        private void bindConfigurationProperties(Set<Class<?>> componentClasses, IndexView index,
                BeanRegistry registry) {
            // Only bind @ConfigurationProperties that are in the active component set.
            // This avoids binding RedisProperties etc. when tests only want CorsConfig.
            for (Class<?> configClass : componentClasses) {
                if (!configClass.isAnnotationPresent(ConfigurationProperties.class)) {
                    continue;
                }
                if (registry.peek(configClass) != null) {
                    continue; // already registered (e.g. by @Bean)
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

        /**
         * Builds a {@link BeanContainer} from the given seed components using
         * transitive dependency expansion (no Jandex classpath scanning).
         * Called by {@link RuntimeApplicationContext#containing(Class...)}.
         */
        private BeanContainer buildLocal(Class<?>... components) {
            ConfigBinder.setDefaultValueResolver(RuntimeDefaultValueResolver.INSTANCE);

            BeanRegistry registry = new BeanRegistry();
            registry.registerSingleton(RuntimeDiMarker.class, new RuntimeDiMarker());

            IndexView index = JandexIndexLoader.buildIndex();

            Set<Class<?>> seeds = new LinkedHashSet<>();
            for (Class<?> c : components) {
                seeds.add(c);
            }
            Set<Class<?>> componentClasses = transitiveExpand(seeds, index);

            // Pre-bind @ConfigurationProperties
            bindConfigurationProperties(componentClasses, index, registry);

            // Build the full node set
            Set<Object> allNodes = new LinkedHashSet<>(componentClasses);
            allNodes.addAll(registry.singletons().keySet());
            for (Class<?> clazz : componentClasses) {
                if (clazz.isAnnotationPresent(Configuration.class)) {
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(Bean.class)) {
                            allNodes.add(method);
                        }
                    }
                }
            }

            // Phase 1: condition evaluation
            ConditionEvaluator.evaluate(allNodes);
            componentClasses.retainAll(allNodes.stream().filter(n -> n instanceof Class<?>).map(n -> (Class<?>) n)
                    .collect(java.util.stream.Collectors.toSet()));

            // Phase 2: build dependency graph
            DependencyGraph dependencyGraph = new DependencyGraph();
            dependencyGraph.buildGraph(allNodes);
            if (dependencyGraph.hasCircularDependencies()) {
                throw new CircularDependencyException("Circular dependencies detected");
            }

            // Phase 3: instantiate
            List<Object> instantiationOrder = dependencyGraph.topologicalSort();
            BeanInstantiator instantiator = new BeanInstantiator(registry, dependencyGraph);
            for (Object node : instantiationOrder) {
                if (node instanceof Class<?> clazz) {
                    instantiator.instantiateBean(clazz);
                } else if (node instanceof Method method) {
                    instantiator.invokeBeanProducer(method);
                }
            }

            // Register @RowModel reflective mappers with JdbcTemplate
            registerRowMappers(registry, index);

            // Phase 4: validation
            runValidators(registry);

            return BeanContainer.create(registry, Engine.RUNTIME);
        }

        // ---- Validation ----

        @SuppressWarnings("unchecked")
        private void runValidators(BeanRegistry registry) {
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

    /**
     * Bean instantiator that operates on a {@link BeanRegistry}. Handles
     * constructor injection, {@code @Bean} method invocation, {@link Provider}
     * resolution, interface registration, and AOP proxy wrapping.
     */
    private static final class BeanInstantiator {

        private final BeanRegistry registry;
        private final DependencyGraph dependencyGraph;

        BeanInstantiator(BeanRegistry registry, DependencyGraph dependencyGraph) {
            this.registry = registry;
            this.dependencyGraph = dependencyGraph;
        }

        void instantiateBean(Class<?> clazz) {
            if (registry.peek(clazz) != null) {
                return;
            }
            try {
                Object instance;
                if (clazz.isAnnotationPresent(ConfigurationProperties.class)) {
                    ConfigurationProperties ann = clazz.getAnnotation(ConfigurationProperties.class);
                    instance = ConfigBinder.bind(ann.prefix(), clazz);
                } else {
                    instance = createInstance(clazz);
                }
                registerBean(clazz, instance);
            } catch (Exception e) {
                if (e instanceof NoSuchBeanException nse) {
                    throw nse;
                }
                throw new BeanCreationException("Failed to instantiate bean: " + clazz.getName(), e);
            }
        }

        private Object createInstance(Class<?> clazz) throws ReflectiveOperationException {
            Constructor<?> constructor = dependencyGraph.getConstructorForClass(clazz);
            Object[] args = resolveArgs(constructor.getParameterTypes(), constructor.getGenericParameterTypes());
            return constructor.newInstance(args);
        }

        private Object[] resolveArgs(Class<?>[] paramTypes, Type[] genericTypes) {
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> paramType = paramTypes[i];
                Type genericType = genericTypes[i];
                if (paramType == List.class && genericType instanceof ParameterizedType pt) {
                    Type elementType = pt.getActualTypeArguments()[0];
                    if (elementType instanceof Class<?> elementClass) {
                        args[i] = registry.getBeans(elementClass);
                    } else {
                        args[i] = registry.getBean(paramType);
                    }
                } else {
                    // ApplicationContext was injected before — now we don't have access to it here
                    // so we throw (no production code uses this in runtime engine anymore)
                    if (paramType == summer.core.BeanContainer.class) {
                        throw new BeanCreationException(ErrorCode.BEAN_CREATION_FAILED,
                                "ApplicationContext injection is not supported by the runtime engine. Use BeanContainer from caller.");
                    }
                    args[i] = registry.getBean(paramType);
                }
            }
            return args;
        }

        private void registerBean(Class<?> clazz, Object instance) {
            if (instance instanceof Provider<?> provider) {
                registerProvider(clazz, provider);
            } else {
                registerRegularBean(clazz, instance);
            }
        }

        private void registerProvider(Class<?> clazz, Provider<?> provider) {
            Object providedInstance = provider.provide();
            Class<?> providedType = getProvidedType(clazz);
            registry.registerSingleton(providedType, providedInstance);
            registry.registerSingleton(clazz, provider);
        }

        private void registerRegularBean(Class<?> clazz, Object instance) {
            List<MethodInterceptor> matchingInterceptors = resolveMatchingInterceptors(clazz);
            Object proxy = RuntimeAopProcessor.applyProxy(instance, clazz, matchingInterceptors);
            // Concrete class key keeps the raw instance
            registry.registerSingleton(clazz, instance);
            // Interfaces get the proxy (first-wins)
            registerAllInterfaces(clazz, proxy);
        }

        private void registerAllInterfaces(Class<?> clazz, Object instance) {
            for (Class<?> iface : clazz.getInterfaces()) {
                registry.registerInterface(iface, instance);
                registerAllInterfaces(iface, instance);
            }
        }

        void invokeBeanProducer(Method producer) {
            try {
                Class<?> configClass = producer.getDeclaringClass();
                Object configBean = registry.getBean(configClass);
                Object[] args = resolveArgs(producer.getParameterTypes(), producer.getGenericParameterTypes());
                Object result = producer.invoke(configBean, args);
                if (result == null) {
                    return;
                }
                Class<?> producedType = producer.getReturnType();
                List<MethodInterceptor> matchingInterceptors = resolveMatchingInterceptors(producedType);
                Object proxy = RuntimeAopProcessor.applyProxy(result, producedType, matchingInterceptors);
                registry.registerSingleton(producedType, result);
                registerAllInterfaces(producedType, proxy);
            } catch (java.lang.reflect.InvocationTargetException | IllegalAccessException e) {
                throw new BeanCreationException("Failed to invoke @Bean method: " + producer.getName(), e);
            }
        }

        private List<MethodInterceptor> resolveMatchingInterceptors(Class<?> beanClass) {
            Set<Class<?>> interceptorClasses = dependencyGraph.getMatchingInterceptorClasses(beanClass);
            if (interceptorClasses.isEmpty()) {
                return List.of();
            }
            List<MethodInterceptor> result = new ArrayList<>();
            for (Class<?> interceptorClass : interceptorClasses) {
                Object interceptor = registry.getBean(interceptorClass);
                if (interceptor instanceof MethodInterceptor mi) {
                    result.add(mi);
                }
            }
            return result;
        }

        private static Class<?> getProvidedType(Class<?> providerClass) {
            for (Type iface : providerClass.getGenericInterfaces()) {
                if (iface instanceof ParameterizedType pt && pt.getRawType() == Provider.class) {
                    return (Class<?>) pt.getActualTypeArguments()[0];
                }
            }
            throw new BeanCreationException("Could not determine provided type for: " + providerClass.getName());
        }
    }

    /**
     * Three-phase condition evaluator for the Runtime DI engine.
     *
     * <p>
     * Phase 1: Build dependency graph from {@code @ConditionalOnBean} edges,
     * topological sort. Phase 2: {@code @Replaces} — mark redirects (original
     * → replacement); originals stay until Phase 3 cleanup. Phase 3:
     * {@code @ConditionalOnBean} evaluated in topo order with redirect
     * resolution; replacements that get removed restore their originals.
     * </p>
     */
    private static final class ConditionEvaluator {

        private ConditionEvaluator() {
        }

        static void evaluate(Set<Object> nodes) {
            List<Object> topoOrder = buildTopologicalOrder(nodes);

            // Pre-compute @Bean return types for @ConditionalOnBean fallback matching
            Set<Class<?>> beanReturnTypes = new HashSet<>();
            for (Object node : nodes) {
                if (node instanceof Class<?> clazz
                        && clazz.isAnnotationPresent(Configuration.class)) {
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(Bean.class)) {
                            beanReturnTypes.add(method.getReturnType());
                        }
                    }
                }
            }

            // @Replaces — mark redirects
            Map<Object, Object> redirects = new java.util.HashMap<>();
            resolveReplaces(nodes, redirects);

            // @ConditionalOnBean — evaluate in topo order
            resolveConditionalOnBean(nodes, topoOrder, redirects, beanReturnTypes);
        }

        private static List<Object> buildTopologicalOrder(Set<Object> nodes) {
            Map<Object, Set<Object>> deps = new java.util.HashMap<>();
            for (Object node : nodes) {
                Class<?> required = getRequiredType(node);
                if (required != null) {
                    deps.computeIfAbsent(node, k -> new HashSet<>()).add(required);
                }
            }
            Set<Object> visited = new HashSet<>();
            Set<Object> inStack = new HashSet<>();
            List<Object> order = new ArrayList<>();
            for (Object node : nodes) {
                dfs(node, deps, visited, inStack, order);
            }
            return order;
        }

        private static void dfs(Object node, Map<Object, Set<Object>> deps, Set<Object> visited, Set<Object> inStack,
                List<Object> order) {
            if (visited.contains(node)) {
                return;
            }
            visited.add(node);
            inStack.add(node);
            Set<Object> nodeDeps = deps.getOrDefault(node, Set.of());
            for (Object dep : nodeDeps) {
                if (!visited.contains(dep)) {
                    dfs(dep, deps, visited, inStack, order);
                }
            }
            inStack.remove(node);
            order.add(node);
        }

        private static Class<?> getRequiredType(Object node) {
            if (node instanceof Class<?> clazz) {
                ConditionalOnBean cond = clazz.getAnnotation(ConditionalOnBean.class);
                return cond != null ? cond.value() : null;
            }
            if (node instanceof Method method) {
                ConditionalOnBean cond = method.getAnnotation(ConditionalOnBean.class);
                return cond != null ? cond.value() : null;
            }
            return null;
        }

        private static void resolveReplaces(Set<Object> nodes, Map<Object, Object> redirects) {
            // Class-level @Replaces
            for (Object node : new ArrayList<>(nodes)) {
                if (!(node instanceof Class<?> clazz)) {
                    continue;
                }
                Replaces replaces = clazz.getAnnotation(Replaces.class);
                if (replaces == null) {
                    continue;
                }
                Class<?> targetType = replaces.value();
                Object target = findNodeByType(nodes, targetType);
                if (target == null) {
                    throw new NoSuchBeanException("@Replaces target not found: " + targetType.getName());
                }
                redirects.put(target, node);
                // Also redirect @Bean methods declared on the replaced class
                for (Object n : nodes) {
                    if (n instanceof Method m && m.getDeclaringClass() == targetType) {
                        redirects.put(m, node);
                    }
                }
            }

            // Method-level @Replaces (on @Bean methods)
            for (Object node : new ArrayList<>(nodes)) {
                if (!(node instanceof Method method)) {
                    continue;
                }
                if (!method.isAnnotationPresent(Bean.class)) {
                    continue;
                }
                Replaces replaces = method.getAnnotation(Replaces.class);
                if (replaces == null) {
                    continue;
                }
                Class<?> targetType = replaces.value();
                Object target = findNodeByReturnType(nodes, targetType, method);
                if (target == null) {
                    throw new NoSuchBeanException("@Replaces target not found: " + targetType.getName());
                }
                redirects.put(target, node);
            }
        }

        private static void resolveConditionalOnBean(Set<Object> nodes, List<Object> topoOrder,
                Map<Object, Object> redirects, Set<Class<?>> beanReturnTypes) {
            for (Object node : topoOrder) {
                if (!nodes.contains(node)) {
                    continue;
                }
                Class<?> requiredType = getRequiredType(node);
                if (requiredType == null) {
                    continue;
                }
                boolean satisfied = false;
                for (Object n : nodes) {
                    Class<?> providedType = getProvidedType(n);
                    if (providedType == null) {
                        continue;
                    }
                    if (requiredType.isAssignableFrom(providedType)) {
                        satisfied = true;
                        break;
                    }
                    Object redirectTarget = redirects.get(n);
                    if (redirectTarget != null) {
                        Class<?> redirectType = getProvidedType(redirectTarget);
                        if (redirectType != null && requiredType.isAssignableFrom(redirectType)) {
                            satisfied = true;
                            break;
                        }
                    }
                }
                if (!satisfied) {
                    for (Class<?> returnType : beanReturnTypes) {
                        if (requiredType.isAssignableFrom(returnType)) {
                            satisfied = true;
                            break;
                        }
                    }
                }
                if (!satisfied) {
                    nodes.remove(node);
                    if (node instanceof Class<?> clazz) {
                        nodes.removeIf(n -> n instanceof Method m && m.getDeclaringClass() == clazz);
                    }
                    // Restore original if a replacement is removed
                    redirects.entrySet().removeIf(entry -> entry.getValue() == node);
                }
            }
            // Cleanup: remove originals whose replacements survived
            for (Map.Entry<Object, Object> entry : new ArrayList<>(redirects.entrySet())) {
                Object original = entry.getKey();
                Object replacement = entry.getValue();
                if (nodes.contains(replacement)) {
                    nodes.remove(original);
                    if (original instanceof Class<?> clazz) {
                        nodes.removeIf(n -> n instanceof Method m && m.getDeclaringClass() == clazz);
                    }
                }
            }
        }

        private static Object findNodeByType(Set<Object> nodes, Class<?> type) {
            for (Object node : nodes) {
                if (getProvidedType(node) == type) {
                    return node;
                }
            }
            return null;
        }

        private static Object findNodeByReturnType(Set<Object> nodes, Class<?> returnType, Method replacement) {
            for (Object node : nodes) {
                // Match a Class node whose type is the return type
                if (node instanceof Class<?> c && c == returnType) {
                    return c;
                }
                // Match a Method node whose return type matches
                if (node instanceof Method m && m.getReturnType() == returnType && m != replacement) {
                    return m;
                }
            }
            return null;
        }

        private static Class<?> getProvidedType(Object node) {
            if (node instanceof Class<?> clazz) {
                return clazz;
            }
            if (node instanceof Method method) {
                return method.getReturnType();
            }
            return null;
        }
    }
}