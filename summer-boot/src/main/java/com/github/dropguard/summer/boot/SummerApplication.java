package com.github.dropguard.summer.boot;

import com.github.dropguard.summer.core.ApplicationRunner;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.config.ConfigBinder;
import com.github.dropguard.summer.core.config.FrameworkConfig;
import com.github.dropguard.summer.core.config.ShutdownConfig;
import com.github.dropguard.summer.core.exception.ConfigurationException;
import com.github.dropguard.summer.engine.DiEngine;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
     * <p>Precedence: {@code -Dsummer.engine} system property, {@code SUMMER_ENGINE} environment
     * variable, YAML value, then the default. A higher-priority value short-circuits lower ones.
     */
    static Engine resolveBootstrapEngine(String envOverride) {
        return DiEngine.resolveEngine(() -> engineFromConfiguration(envOverride));
    }

    private static Engine engineFromConfiguration(String envOverride) {
        String raw = envOverride;
        if (raw == null) {
            Object value =
                    new ConfigBinder()
                            .bindSection(ConfigBinder.BindingContext.of(), "summer")
                            .get("engine");
            if (value == null) return FrameworkConfig.DEV_ENGINE;
            raw = value.toString();
        }
        Engine configured = Engine.fromString(raw);
        if (configured == null) {
            throw new ConfigurationException(
                    ErrorCode.CONFIG_PARSE_ERROR,
                    "Blank summer.engine value (expected runtime or aot)");
        }
        return configured;
    }

    private BeanContainer doStart(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();
        // The ordered list of middleware classes declared via apply(...) is passed
        // as a boot-time external bean (keyed by the plain List type) so the web
        // server runner can apply them in declaration order. Middleware beans
        // annotated with @GlobalMiddleware are collected automatically without this.
        BeanContainer context =
                DiEngine.create(
                        resolveBootstrapEngine(System.getenv("SUMMER_ENGINE")), middlewareEntries);
        startContainer(context);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info(
                "Started Summer application in {} ms (JVM uptime: {} ms).",
                elapsed,
                java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
        return context;
    }

    /**
     * Fallback budget when {@link ShutdownConfig} itself cannot be resolved (the failure being
     * cleaned up may be exactly that). Mirrors {@code ShutdownConfig.timeoutMs()}'s
     * {@code @WithDefault}: the boot layer is reflection-free, so the annotation is unreadable
     * here.
     */
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 10_000L;

    /**
     * Registers the shutdown hook, then runs every {@link ApplicationRunner}. If any step fails,
     * the hook is withdrawn and the container is closed here, within the same shutdown budget,
     * before the failure propagates: a failed start never leaves a half-started container behind,
     * and a bean stuck in {@code close()} cannot hang the failing process.
     */
    static void startContainer(BeanContainer context) throws Exception {
        Thread shutdownHook = null;
        long shutdownTimeoutMs = DEFAULT_SHUTDOWN_TIMEOUT_MS;
        try {
            // Single shutdown budget (Quarkus quarkus.shutdown.timeout model): shutdown.timeout-ms
            // is the total time allowed for the whole teardown (servers draining in-flight
            // requests, then AutoCloseable beans closing), after which the JVM exits regardless.
            shutdownTimeoutMs = context.getBean(ShutdownConfig.class).timeoutMs();
            long hookTimeoutMs = shutdownTimeoutMs;
            shutdownHook =
                    new Thread(() -> closeOnShutdown(context, hookTimeoutMs), "Summer-Shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            System.out.println(Banner.format(context.engine().name()));
            for (var runner : context.getBeans(ApplicationRunner.class)) {
                runner.run(context);
            }
        } catch (Exception | Error startupFailure) {
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException shutdownInProgress) {
                    // The hook is already closing the container; close() is idempotent.
                    startupFailure.addSuppressed(shutdownInProgress);
                }
            }
            closeAfterStartupFailure(context, shutdownTimeoutMs, startupFailure);
            throw startupFailure;
        }
    }

    private static void closeOnShutdown(BeanContainer context, long timeoutMs) {
        log.info("Shutting down BeanContainer...");
        try {
            Throwable failure = closeWithin(context, timeoutMs);
            if (failure != null) {
                log.error("Error during BeanContainer shutdown", failure);
            }
        } catch (TimeoutException e) {
            log.warn("Shutdown timeout ({}ms) exceeded, forcing exit.", timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for BeanContainer shutdown", e);
        }
    }

    private static void closeAfterStartupFailure(
            BeanContainer context, long timeoutMs, Throwable startupFailure) {
        try {
            Throwable cleanupFailure = closeWithin(context, timeoutMs);
            if (cleanupFailure != null && cleanupFailure != startupFailure) {
                startupFailure.addSuppressed(cleanupFailure);
            }
        } catch (TimeoutException e) {
            startupFailure.addSuppressed(
                    new TimeoutException(
                            "Container cleanup after the failed start exceeded the "
                                    + timeoutMs
                                    + "ms shutdown budget; abandoning it"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            startupFailure.addSuppressed(e);
        }
    }

    /**
     * Closes the container on a daemon worker, waiting at most {@code timeoutMs}, and returns what
     * {@code close()} threw (or {@code null}). The worker must be a daemon: on the startup-failure
     * path a close stuck past the budget would otherwise keep the JVM alive after the failure has
     * propagated out of {@code main}.
     */
    private static Throwable closeWithin(BeanContainer context, long timeoutMs)
            throws TimeoutException, InterruptedException {
        var executor =
                Executors.newSingleThreadExecutor(
                        task -> {
                            Thread worker = new Thread(task, "Summer-Container-Close");
                            worker.setDaemon(true);
                            return worker;
                        });
        try {
            executor.submit(
                            () -> {
                                context.close();
                                return null;
                            })
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } finally {
            executor.shutdownNow();
        }
    }
}
