package com.github.dropguard.summer.core.config;

/**
 * Global configuration for graceful shutdown.
 *
 * <p>{@code timeoutMs} is the <em>single shutdown budget</em> (Quarkus {@code
 * quarkus.shutdown.timeout} model): the total time allowed for the whole teardown — servers
 * draining in-flight requests, then AutoCloseable beans closing — after which the JVM exits
 * regardless. It bounds both the servers' graceful drain ({@code NettyServerRunner}/ {@code
 * GrpcServerRunner}) and the JVM-exit guard in {@code SummerApplication}'s shutdown hook.
 *
 * <p>There is no hard traffic-isolation sleep: an instance signals shutdown via {@code
 * ApplicationState.beginShutdown()} (readiness probe returns 503), letting the load balancer stop
 * routing before the server stops accepting — so the drain window is driven by LB polling, not a
 * fixed sleep.
 *
 * <p>The default (10s) is tuned to sit inside a bare {@code docker stop}'s 10s SIGKILL budget, so
 * draining in-flight requests is not cut short by a forced kill. On Kubernetes, raise {@code
 * terminationGracePeriodSeconds} above this (and any other teardown work) so the drain can
 * complete.
 */
@ConfigMapping(prefix = "shutdown")
public interface ShutdownConfig {

    @WithDefault("10000")
    Long timeoutMs();
}
