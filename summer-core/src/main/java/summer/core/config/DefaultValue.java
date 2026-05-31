package summer.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a default value for a configuration property when the value is not
 * present in the YAML configuration file.
 *
 * <p>
 * Example:
 * </p>
 *
 * <pre>{@code
 * @ConfigurationProperties(prefix = "jwt")
 * public record JwtProperties(String secret, @DefaultValue("3600000") long expiration,
 * 		@DefaultValue("3") int maxRetries) {
 * }
 * }</pre>
 *
 * <p>
 * The value is parsed according to the target type:
 * </p>
 * <ul>
 * <li>{@code String} — used as-is</li>
 * <li>{@code int/Integer} — parsed with {@code Integer.parseInt}</li>
 * <li>{@code long/Long} — parsed with {@code Long.parseLong}</li>
 * <li>{@code boolean/Boolean} — parsed with {@code Boolean.parseBoolean}</li>
 * <li>{@code double/Double} — parsed with {@code Double.parseDouble}</li>
 * </ul>
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultValue {

	/**
	 * The default value as a string. Will be converted to the target type.
	 */
	String value();
}
