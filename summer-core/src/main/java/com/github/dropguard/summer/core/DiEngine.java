package com.github.dropguard.summer.core;

import com.github.dropguard.summer.core.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI engine bootstrap. Loads the engine implementation via {@code Class.forName} and invokes its
 * static {@code build()} method.
 */
@Internal
public final class DiEngine {

    private static final Logger log = LoggerFactory.getLogger(DiEngine.class);

    private static final String AOT_CLASS =
            "com.github.dropguard.summer.aot.generated.GeneratedAotContext";
    private static final String RUNTIME_CLASS =
            "com.github.dropguard.summer.runtime.RuntimeContainer";

    /** Command-line override for the DI engine: {@code -Dsummer.engine=runtime|aot}. */
    private static final String ENGINE_PROPERTY = "summer.engine";

    private DiEngine() {}

    /**
     * Creates a {@link BeanContainer} using an explicitly chosen engine. This is the entry point
     * for code paths that already know which engine to use (dual-engine TCK, the test framework's
     * AOT escape hatch). It never consults configuration or system properties.
     *
     * @param engine the engine to build with
     * @param externalBeans boot-time application beans (e.g. the ordered middleware list from
     *     {@code SummerApplication.apply(...)}); never exposed to tests
     */
    public static BeanContainer create(Engine engine, Object... externalBeans) {
        engine = resolveEngine(engine);
        if (engine == Engine.AOT) {
            log.info("[Summer] Building container via AOT engine");
            return invokeBuild(
                    AOT_CLASS,
                    ErrorCode.CONFIG_AOT_CONTEXT_NOT_FOUND,
                    "AOT Context missing. Please ensure 'summer-maven-plugin' is configured for"
                            + " production builds.",
                    externalBeans);
        }
        log.info("[Summer] Building container via RUNTIME engine");
        return invokeBuild(
                RUNTIME_CLASS,
                ErrorCode.CONFIG_RUNTIME_NOT_ON_CLASSPATH,
                "Runtime engine not found: " + RUNTIME_CLASS,
                externalBeans);
    }

    /**
     * Resolves the effective engine: system property override wins, otherwise the configured
     * default (typically from {@code FrameworkConfig.engine()}).
     */
    public static Engine resolveEngine(Engine configured) {
        Engine override = Engine.fromString(System.getProperty(ENGINE_PROPERTY));
        if (override != null) {
            log.info("[Summer] Engine override (-D{}): {}", ENGINE_PROPERTY, override);
            return override;
        }
        log.info("[Summer] Engine from configuration: {}", configured);
        return configured;
    }

    private static BeanContainer invokeBuild(
            String className, ErrorCode notFoundCode, String notFoundMsg, Object... externalBeans) {
        try {
            return loadCompiledEngine(className, externalBeans);
        } catch (ConfigurationException e) {
            // propagate the not-found variant unchanged
            throw e;
        }
    }

    /**
     * The single, framework-recognized boundary at which a compiled engine class is loaded and its
     * static {@code build} method invoked. Both the production path ({@link #create}) and the
     * test-time AOT compiler ({@code AotEngine}) funnel through here, so reflective loading of
     * generated classes lives in exactly one place — the core engine loader — and never leaks into
     * the AOT or runtime modules.
     *
     * <p>{@code build} is matched by its argument array type, so both {@code build(Object...)}
     * (production) and {@code build(MockedBean[])} (test-time AOT) resolve through the same
     * reflective call.
     *
     * @param className fully-qualified name of the compiled engine / container class
     * @param args arguments passed to the static {@code build} method
     * @return the built container
     */
    public static BeanContainer loadCompiledEngine(String className, Object... args) {
        return loadCompiledEngine(className, new java.net.URL[0], args);
    }

    /**
     * Loads a compiled engine class and invokes its static {@code build} method, optionally
     * extending the search path with extra classpath elements (e.g. the temp directory an AOT
     * compiler just emitted {@code .class} files into). When {@code extraClasspath} is empty the
     * behavior is identical to the plain overload — the class is resolved against the current
     * context class loader, as the production path expects (generated {@code GeneratedAotContext}
     * lives in {@code target/classes}). The extra-URL form is what lets the test-time AOT compiler
     * load classes it just compiled to a scratch directory that is not (and must not be) on the
     * application classpath.
     *
     * @param className fully-qualified name of the compiled engine / container class
     * @param extraClasspath directories/jars to search in addition to the context loader
     * @param args arguments passed to the static {@code build} method
     * @return the built container
     */
    public static BeanContainer loadCompiledEngine(
            String className, java.net.URL[] extraClasspath, Object... args) {
        try {
            log.debug(
                    "[Summer] Loading engine class {} (extraClasspath={})",
                    className,
                    extraClasspath.length);
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (extraClasspath.length > 0) {
                loader = new java.net.URLClassLoader(extraClasspath, loader);
            }
            Class<?> clazz = Class.forName(className, true, loader);
            return (BeanContainer)
                    clazz.getMethod("build", args.getClass()).invoke(null, (Object) args);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(
                    ErrorCode.CONFIG_AOT_CONTEXT_NOT_FOUND,
                    "Engine class not found on classpath: " + className,
                    e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new ConfigurationException(
                    ErrorCode.INTERNAL_ERROR, "Engine initialization failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new ConfigurationException(
                    ErrorCode.INTERNAL_ERROR, "Failed to instantiate engine: " + className, e);
        }
    }
}
