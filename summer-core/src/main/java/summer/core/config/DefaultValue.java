package summer.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a default value for a {@link ConfigurationProperties} record
 * component. The value is applied when the property is <em>unbound</em> —
 * i.e.&nbsp;the key is absent from the configuration map.
 *
 * <p>
 * The {@link #value()} is always a {@code String}; it is parsed to the
 * component's type at bind time.
 * </p>
 *
 * <pre>{@code
 * @ConfigurationProperties(prefix = "server")
 * public record ServerProperties(String host, @DefaultValue("8080") int port, @DefaultValue("false") boolean ssl) {
 * }
 * }</pre>
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultValue {
	/**
	 * The default value, expressed as a string. Parsed to the record component's
	 * declared type (supports {@code String}, {@code int}, {@code long},
	 * {@code boolean}, {@code double}, {@code float}, {@code short}, {@code byte}
	 * and their boxed counterparts).
	 */
	String value();
}
