package com.github.dropguard.summer.core.config;

/**
 * Global configuration for graceful shutdown.
 *
 * <p>
 * {@code timeoutMs} bounds how long in-flight requests are allowed to drain
 * before a server's resources are released. There is no hard traffic-isolation
 * sleep: an instance signals shutdown via
 * {@code ApplicationState.beginShutdown()} (readiness probe returns 503),
 * letting the load balancer stop routing before the server stops accepting — so
 * the drain window is driven by LB polling, not a fixed sleep.
 *
 * <p>
 * The default (10s) is tuned to sit inside a bare {@code docker stop}'s 10s
 * SIGKILL budget, so draining in-flight requests is not cut short by a forced
 * kill. On Kubernetes, raise {@code terminationGracePeriodSeconds} above this
 * (and any other teardown work) so the drain can complete.
 */
@ConfigurationProperties(prefix = "com.github.dropguard.summer.shutdown")
public record ShutdownConfig(@DefaultValue("10000") Long timeoutMs) {
}
