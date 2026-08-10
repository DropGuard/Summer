package com.github.dropguard.summer.engine;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI engine bootstrap. Loads the engine implementation via {@code Class.forName} and invokes its
 * static {@code build()} method.
 *
 * <p>The production engine contract is the reflective static {@code build(Object...)} entry point:
 * the AOT engine's generated {@code GeneratedAotContext} and the runtime engine's {@code
 * RuntimeBootstrap} both provide it, and this class is the single framework-recognized boundary at
 * which such a compiled engine class is loaded. Lives in the engine module (with {@code
 * ContainerEngine}/{@code ContainerEngines}) so engine discovery/loading is aggregated in one layer
 * — summer-core stays a pure contract layer with no reflection.
 *
 * <p>Why a name-based reflective contract instead of the {@link ContainerEngines} ServiceLoader
 * SPI? The AOT engine class ({@code GeneratedAotContext}) is generated per application by the Maven
 * plugin, so it cannot be registered in a module's static {@code META-INF/services} file —
 * ServiceLoader discovery only covers the fixed, buildable-by-name classes {@code AotContainer} and
 * {@code RuntimeContainer} (the test-infra path). Production boot therefore resolves the effective
 * engine (config / {@code -Dsummer.engine}) and loads the matching pre-generated class by name; the
 * two seams serve different execution models, not duplicated logic.
 */
@Internal
public final class DiEngine {

    private static final Logger log = LoggerFactory.getLogger(DiEngine.class);

    private static final String AOT_CLASS =
            "com.github.dropguard.summer.aot.generated.GeneratedAotContext";
    private static final String RUNTIME_CLASS =
            "com.github.dropguard.summer.runtime.RuntimeBootstrap";

    /** Command-line override for the DI engine: {@code -Dsummer.engine=runtime|aot}. */
    private static final String ENGINE_PROPERTY = "summer.engine";

    private DiEngine() {}

    /**
     * Creates a {@link BeanContainer} using an explicitly chosen, ALREADY-RESOLVED engine. This is
     * the entry point for code paths that know which engine to use (production boot via {@code
     * SummerApplication.resolveBootstrapEngine()}; the dual-engine TCK; the test framework's AOT
     * escape hatch). It never consults configuration or system properties — an explicit choice is
     * honored as-is, never overridden by {@code -Dsummer.engine} (the override is applied once, in
     * {@link #resolveEngine}, by the boot layer).
     *
     * @param engine the engine to build with
     * @param externalBeans boot-time application beans (e.g. the ordered middleware list from
     *     {@code SummerApplication.apply(...)}); never exposed to tests
     */
    public static BeanContainer create(Engine engine, Object... externalBeans) {
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
            // loadCompiledEngine wraps class-not-found with the generic AOT code (2005); re-wrap
            // with the engine-specific code the caller selected (2006 for runtime, 2005 for AOT)
            // so the reported ErrorCode matches the missing engine.
            if (e.errorCode() == ErrorCode.CONFIG_AOT_CONTEXT_NOT_FOUND
                    && notFoundCode != ErrorCode.CONFIG_AOT_CONTEXT_NOT_FOUND) {
                throw new ConfigurationException(notFoundCode, notFoundMsg, e);
            }
            throw e;
        }
    }

    /**
     * The single, framework-recognized boundary at which a compiled engine class is loaded and its
     * static {@code build} method invoked. Both the production path ({@link #create}) and the
     * test-time AOT compiler ({@code AotEngine}) funnel through here, so reflective loading of
     * generated classes lives in exactly one place — the engine layer — and never leaks into the
     * contract layer.
     *
     * <p>{@code build} is matched by its argument array type; the generated/runtime entry points
     * expose the single {@code build(Object...)} contract (external beans; mocks travel as a {@code
     * MockedBean[]} element on the test-time AOT path).
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
            final java.net.URLClassLoader compiledLoader =
                    extraClasspath.length > 0
                            ? new java.net.URLClassLoader(extraClasspath, loader)
                            : null;
            if (compiledLoader != null) {
                loader = compiledLoader;
            }
            // True once the loader is owned by the container's shutdown task — the finally below
            // then leaves it to the container's close.
            final boolean[] loaderAttached = {false};
            Class<?> clazz;
            try {
                // The class load itself is inside the try: a NoClassDefFoundError / LinkageError
                // here (not just an InvocationTargetException from build()) must also close the
                // loader, or the scratch dir's deleteOnExit stays broken on that path too.
                clazz = Class.forName(className, true, loader);
                BeanContainer container =
                        (BeanContainer)
                                clazz.getMethod("build", args.getClass())
                                        .invoke(null, (Object) args);
                // Bound the compiled-class cache's footprint: the URLClassLoader bridges the
                // scratch temp dir (the generated classes are not on the classpath), and the JVM
                // pins a loaded class's defining loader until the process exits. Closing it when
                // the container closes frees the jar/file handles so the temp dir's deleteOnExit
                // actually runs. Loaded classes keep working after close() — only the handles are
                // released — so the container's own teardown (Phase 1 runners, Phase 2
                // AutoCloseable beans) is unaffected.
                if (compiledLoader != null) {
                    container.addShutdownTask(
                            () -> {
                                try {
                                    compiledLoader.close();
                                } catch (Exception ignored) {
                                }
                            });
                    loaderAttached[0] = true;
                }
                return container;
            } finally {
                // Failure path: if build() threw, the loader is never reachable via the container
                // — close it here so the scratch dir's deleteOnExit still runs.
                if (compiledLoader != null && !loaderAttached[0]) {
                    try {
                        compiledLoader.close();
                    } catch (Exception ignored) {
                    }
                }
            }
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
