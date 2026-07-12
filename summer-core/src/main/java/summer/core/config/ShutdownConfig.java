package summer.core.config;

/**
 * Global configuration for Graceful Shutdown.
 */
@ConfigurationProperties(prefix = "summer.shutdown")
public record ShutdownConfig(@DefaultValue("0") Long sleepMs, @DefaultValue("30000") Long timeoutMs) {
}
