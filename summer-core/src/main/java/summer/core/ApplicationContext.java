package summer.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;
import summer.aop.MethodInterceptor;
import summer.aop.ProxyFactory;

/**
 * The core Summer application context that manages beans and their
 * dependencies. This is the main entry point for the DI container.
 */
public class ApplicationContext {

    private static volatile ApplicationContext INSTANCE;

    private final ComponentScanner componentScanner;
    private final DependencyGraph dependencyGraph;
    private final Map<Class<?>, Object> singletons = new HashMap<>();

    /**
     * Scans the given package for @Component annotated classes and initializes the
     * context.
     */
    public static ApplicationContext scan(String packageName) {
        ApplicationContext context = new ApplicationContext();
        context.componentScanner.scan(packageName);
        context.initializeBeans();
        INSTANCE = context;
        return context;
    }

    /**
     * Constructor to initialize the context with default scanner and graph.
     */
    public ApplicationContext() {
        this.componentScanner = new ComponentScanner();
        this.dependencyGraph = new DependencyGraph();
    }

    /**
     * Register a component class directly with the context.
     */
    public void registerComponent(Class<?> clazz) {
        componentScanner.registerComponent(clazz);
    }

    /**
     * Initialize all beans by resolving their dependencies and instantiating them.
     */
    public void initializeBeans() {
        // Build dependency graph
        dependencyGraph.buildGraph(componentScanner.getComponentClasses());

        // Check for circular dependencies
        if (dependencyGraph.hasCircularDependencies()) {
            throw new SummerException("Circular dependencies detected");
        }

        // Sort beans by dependency order
        List<Class<?>> instantiationOrder = dependencyGraph.topologicalSort();

        // Instantiate beans
        for (Class<?> clazz : instantiationOrder) {
            instantiateBean(clazz);
        }
    }

    private Object instantiateBean(Class<?> clazz) {
        if (singletons.containsKey(clazz)) {
            return singletons.get(clazz);
        }

        Constructor<?> constructor = dependencyGraph.getConstructorForClass(
            clazz
        );
        // Resolve dependencies
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
            .map(paramType -> {
                if (paramType == ApplicationContext.class) {
                    return this;
                }
                return getBean(paramType);
            })
            .toArray();

        try {
            Object instance = constructor.newInstance(dependencies);
            
            // Apply AOP proxies if interceptors are present and target has interfaces
            if (instance.getClass().getInterfaces().length > 0 && !(instance instanceof MethodInterceptor) && !(instance instanceof Provider)) {
                List<MethodInterceptor> interceptors = getBeansOfType(MethodInterceptor.class).stream()
                        .filter(interceptor -> interceptor.supports(clazz))
                        .collect(Collectors.toList());
                
                if (!interceptors.isEmpty()) {
                    instance = ProxyFactory.createProxy(instance, interceptors);
                }
            }
            
            // Handle Provider pattern
            if (instance instanceof Provider<?> provider) {
                Object providedInstance = provider.provide();
                Class<?> providedType = getProvidedType(clazz);
                singletons.put(providedType, providedInstance);
                singletons.put(clazz, instance);
                return providedInstance;
            }
            
            singletons.put(clazz, instance);
            return instance;
        } catch (
            InstantiationException
            | IllegalAccessException
            | InvocationTargetException e
        ) {
            throw new SummerException(
                "Failed to instantiate bean: " + clazz.getName(),
                e
            );
        }
    }

    private Class<?> getProvidedType(Class<?> providerClass) {
        for (java.lang.reflect.Type iface : providerClass.getGenericInterfaces()) {
            if (iface instanceof java.lang.reflect.ParameterizedType pt) {
                if (pt.getRawType() == Provider.class) {
                    return (Class<?>) pt.getActualTypeArguments()[0];
                }
            }
        }
        throw new SummerException("Could not determine provided type for: " + providerClass.getName());
    }

    /**
     * Gets a bean instance of the given type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        Object instance = singletons.get(type);
        if (instance != null) {
            return (T) instance;
        }

        // Check if this type is a component
        if (componentScanner.getComponentClasses().contains(type)) {
            return (T) instantiateBean(type);
        }

        // Look for a component that implements the interface
        List<Class<?>> implementingClasses = componentScanner
            .getComponentClasses()
            .stream()
            .filter(
                clazz -> type.isAssignableFrom(clazz) && !clazz.isInterface()
            )
            .collect(Collectors.toList());

        if (!implementingClasses.isEmpty()) {
            // If there's more than one implementation, prefer non-default ones
            // (e.g. prioritize HibernateBodyValidator over DefaultBodyValidator)
            Class<?> selectedClass = implementingClasses
                .stream()
                .filter(clazz ->
                    !clazz.getName().startsWith("summer.validation.Default")
                )
                .findFirst()
                .orElse(implementingClasses.get(0));
            return (T) getBean(selectedClass);
        }

        throw new SummerException("No bean found of type: " + type.getName());
    }

    /**
     * Gets all bean instances that are assignable to the given type.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getBeansOfType(Class<T> type) {
        return componentScanner
            .getComponentClasses()
            .stream()
            .filter(
                clazz -> type.isAssignableFrom(clazz) && !clazz.isInterface()
            )
            .map(clazz -> (T) getBean(clazz))
            .collect(Collectors.toList());
    }

    /**
     * Gets all registered component classes.
     */
    public Set<Class<?>> getComponentClasses() {
        return Collections.unmodifiableSet(
            componentScanner.getComponentClasses()
        );
    }

    /**
     * Gets the global singleton application context. Throws an exception if it
     * hasn't been initialized yet.
     */
    public static ApplicationContext getInstance() {
        if (INSTANCE == null) {
            throw new SummerException(
                "ApplicationContext has not been initialized yet"
            );
        }
        return INSTANCE;
    }
}
