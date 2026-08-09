package com.github.dropguard.summer.boot;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.config.ConfigBinder;
import com.github.dropguard.summer.core.config.FrameworkConfig;
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
     * Boots the application. {@code args} is deliberately unused: Summer is configuration-driven
     * (application.yml + {@code ${VAR}} / {@code -D} overrides) — command-line arguments are not a
     * configuration channel. The parameter exists only to match the {@code main} convention.
     */
    /**
     * Resolves the DI engine from {@code summer.engine} without any engine-specific binder. The
     * boot layer must stay engine-neutral: {@code ConfigBinder.bindSection} reads the YAML section
     * (resolving {@code ${VAR}} placeholders for env/{@code -D} overrides), and the fallback
     * mirrors {@code FrameworkConfig.engine()}'s {@code @WithDefault("runtime")}. Production builds
     * flip the YAML to {@code aot} via the Maven plugin.
     *
     * <p>Precedence follows the Spring/Quarkus convention: {@code -Dsummer.engine} system property,
     * then the {@code SUMMER_ENGINE} environment variable, then the YAML value, then the default.
     */
    private static Engine resolveBootstrapEngine() {
        // 1. Explicit system property (-Dsummer.engine) — the highest-precedence override.
        String override = System.getProperty("summer.engine");
        if (override == null || override.isBlank()) {
            // 2. Environment variable (SUMMER_ENGINE) — crosses process boundaries (dev mode's
            // child JVM cannot see the parent's -D properties, only its environment).
            override = System.getenv("SUMMER_ENGINE");
        }

        Map<String, Object> section =
                new ConfigBinder().bindSection(ConfigBinder.BindingContext.of(), "summer");
        Object value = section.get("engine");
        // Fallback mirrors FrameworkConfig.engine()'s @WithDefault via its DEV_ENGINE
        // constant (the boot layer is reflection-free, so the annotation itself is unreadable
        // here) — the default lives in FrameworkConfig, never duplicated as a string.
        String raw =
                override != null
                        ? override
                        : value != null
                                ? value.toString()
                                : FrameworkConfig.DEV_ENGINE.name().toLowerCase();
        try {
            return Engine.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(
                    ErrorCode.CONFIG_PARSE_ERROR,
                    "Invalid summer.engine value: '" + raw + "' (expected runtime or aot)",
                    e);
        }
    }

    private BeanContainer doStart(String[] args) throws Exception {
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

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    // Signal shutdown first so the readiness probe (/health/ready)
                                    // returns
                                    // 503 and the load balancer stops routing before the server
                                    // stops
                                    // accepting. The drain window is then LB-polling-driven,
                                    // bounded by
                                    // com.github.dropguard.summer.shutdown.timeout-ms.
                                    com.github.dropguard.summer.core.ApplicationState
                                            .beginShutdown();

                                    // BeanContainer.close() runs each registered shutdown task
                                    // (servers stop
                                    // accepting, drain in-flight, release resources) in reverse
                                    // order, then
                                    // closes the remaining AutoCloseable beans. This hook only
                                    // guards the
                                    // whole teardown with a JVM-level worst-case timeout so a stuck
                                    // bean
                                    // can't hang exit.
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
                                                .get(30, java.util.concurrent.TimeUnit.SECONDS);
                                    } catch (java.util.concurrent.TimeoutException e) {
                                        log.warn(
                                                "Shutdown grace period (30s) exceeded, forcing"
                                                        + " exit.");
                                    } catch (Exception e) {
                                        log.error("Error waiting for shutdown", e);
                                    } finally {
                                        shutdownExecutor.shutdownNow();
                                    }
                                }));

        log.info("Summer application started.");
        return context;
    }
}
