package com.github.dropguard.summer.boot;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.config.ConfigBinder;
import com.github.dropguard.summer.core.config.FrameworkConfig;
import com.github.dropguard.summer.core.config.ShutdownConfig;
import com.github.dropguard.summer.core.exception.ConfigurationException;
import com.github.dropguard.summer.engine.DiEngine;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Single entry point for Summer applications.
 *
 * <pre>{@code
 * // Engine from `summer.engine` in application.yml (default RUNTIME);
 * // production builds flip it to AOT at build time. Override with -Dsummer.engine.
 * SummerApplication.run(args);
 * }</pre>
 */
public final class SummerApplication {

    private static final Logger log = LoggerFactory.getLogger(SummerApplication.class);

    private java.util.List<Class<? extends com.github.dropguard.summer.web.Middleware>>
            middlewareEntries = new java.util.ArrayList<>();

    public SummerApplication() {}

    /** Main entry point. */
    public static BeanContainer run(String[] args) {
        return new SummerApplication().start(args);
    }

    public SummerApplication apply(
            Class<? extends com.github.dropguard.summer.web.Middleware> clazz) {
        this.middlewareEntries.add(clazz);
        return this;
    }

    public BeanContainer start(String[] args) {
        // Apply the framework's default logging when the app ships no logback config (the
        // Spring Boot LoggingSystem model): a no-config app gets root INFO + the framework
        // pattern instead of logback's DEBUG-level BasicConfigurator noise.
        LoggingConfigurer.configureDefaults();
        // Install the java.util.logging → SLF4J bridge explicitly at startup (not in a
        // static initializer): class-load side effects are implicit, this is explicit.
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        try {
            return doStart(args);
        } catch (Exception e) {
            throw new RuntimeException("Application start failed", e);
        }
    }

    /**
     * Resolves the DI engine from {@code summer.engine} without any engine-specific binder. The
     * boot layer must stay engine-neutral: {@code ConfigBinder.bindSection} reads the YAML section
     * (resolving {@code ${VAR}} placeholders for env/{@code -D} overrides), and the fallback
     * mirrors {@code FrameworkConfig.engine()}'s {@code @WithDefault("runtime")}. Production builds
     * flip the YAML to {@code aot} via the Maven plugin.
     *
     * <p>Precedence follows the Spring/Quarkus convention: {@code -Dsummer.engine} system property
     * (applied last, via the shared {@code DiEngine.resolveEngine} — the one place the override is
     * parsed), then the {@code SUMMER_ENGINE} environment variable, then the YAML value, then the
     * default.
     */
    private static Engine resolveBootstrapEngine() {
        // Environment variable (SUMMER_ENGINE) — crosses process boundaries (dev mode's child JVM
        // cannot see the parent's -D properties, only its environment).
        String envOverride = System.getenv("SUMMER_ENGINE");

        Map<String, Object> section =
                new ConfigBinder().bindSection(ConfigBinder.BindingContext.of(), "summer");
        Object value = section.get("engine");
        // Fallback mirrors FrameworkConfig.engine()'s @WithDefault via its DEV_ENGINE
        // constant (the boot layer is reflection-free, so the annotation itself is unreadable
        // here) — the default lives in FrameworkConfig, never duplicated as a string.
        String raw =
                envOverride != null
                        ? envOverride
                        : value != null
                                ? value.toString()
                                : FrameworkConfig.DEV_ENGINE.name().toLowerCase();
        Engine configured;
        try {
            configured = Engine.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(
                    ErrorCode.CONFIG_PARSE_ERROR,
                    "Invalid summer.engine value: '" + raw + "' (expected runtime or aot)",
                    e);
        }
        // -Dsummer.engine — the highest-precedence override, applied exactly once here.
        // DiEngine.create honors the explicit engine as-is (it no longer re-parses the property).
        return DiEngine.resolveEngine(configured);
    }

    private BeanContainer doStart(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();
        // The ordered list of middleware classes declared via apply(...) is passed
        // as a boot-time external bean (keyed by the plain List type) so the web
        // server runner can apply them in declaration order. Middleware beans
        // annotated with @GlobalMiddleware are collected automatically without this.
        BeanContainer context = DiEngine.create(resolveBootstrapEngine(), this.middlewareEntries);

        System.out.println(Banner.format(context.engine().name()));

        for (var runner :
                context.getBeans(com.github.dropguard.summer.core.ApplicationRunner.class)) {
            runner.run(context);
        }

        // Single shutdown budget (Quarkus quarkus.shutdown.timeout model): shutdown.timeout-ms is
        // the total time allowed for the whole teardown — servers draining in-flight requests,
        // then AutoCloseable beans closing — after which the JVM exits regardless. Captured here so
        // the hook reads a value, not the container, and a misconfigured timeout fails at startup
        // rather than silently at exit.
        long shutdownTimeoutMs = context.getBean(ShutdownConfig.class).timeoutMs();

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    // The shutdown signal (readiness probe -> 503, so the load
                                    // balancer stops routing) is owned by BeanContainer.close():
                                    // it fires ApplicationState.beginShutdown() first, then drains
                                    // servers and closes beans in order. This hook only triggers
                                    // that close flow — it never touches application state
                                    // directly, so any path (hook, explicit close(), test) stays
                                    // consistent. It waits at most shutdownTimeoutMs so a stuck
                                    // bean can't hang exit.
                                    log.info("Shutting down BeanContainer...");
                                    java.util.concurrent.ExecutorService shutdownExecutor =
                                            java.util.concurrent.Executors
                                                    .newSingleThreadExecutor();
                                    try {
                                        shutdownExecutor
                                                .submit(
                                                        () -> {
                                                            try {
                                                                context.close();
                                                            } catch (Exception e) {
                                                                log.error(
                                                                        "Error during BeanContainer"
                                                                                + " shutdown",
                                                                        e);
                                                            }
                                                        })
                                                .get(
                                                        shutdownTimeoutMs,
                                                        java.util.concurrent.TimeUnit.MILLISECONDS);
                                    } catch (java.util.concurrent.TimeoutException e) {
                                        log.warn(
                                                "Shutdown timeout ("
                                                        + shutdownTimeoutMs
                                                        + "ms) exceeded, forcing exit.");
                                    } catch (Exception e) {
                                        log.error("Error waiting for shutdown", e);
                                    } finally {
                                        shutdownExecutor.shutdownNow();
                                    }
                                }));

        long elapsed = System.currentTimeMillis() - startTime;
        log.info(
                "Started Summer application in {} ms (JVM uptime: {} ms).",
                elapsed,
                java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
        return context;
    }
}
