package summer.tck.di.missing;

import summer.core.config.ConfigurationProperties;

/**
 * Test fixture: @ConfigurationProperties record where ALL fields are required
 * (no @DefaultValue). Used to verify that missing fields throw
 * MissingFieldException.
 */
@ConfigurationProperties(prefix = "strict")
public record StrictProperties(String apiKey) {
}
