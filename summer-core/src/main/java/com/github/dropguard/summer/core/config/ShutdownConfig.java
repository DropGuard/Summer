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
 * <p>Shutdown sequence: readiness flips to 503 ({@code ApplicationState.beginShutdown()}), the
 * server stops accepting, in-flight requests drain within the budget, event loops release with
 * whatever remains of it. There is deliberately NO in-app wait between the 503 and stop-accepting —
 * Spring Boot and Quarkus don't have one either; stopping the instance from receiving new traffic
 * during readiness-propagation delay is the orchestrator's job (Kubernetes removes the endpoint
 * before/around SIGTERM). On platforms without endpoint propagation (bare {@code docker stop}), new
 * requests may be refused during the drain — that is expected, not a bug.
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
