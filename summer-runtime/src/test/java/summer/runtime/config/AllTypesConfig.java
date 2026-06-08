package summer.runtime.config;

import summer.core.config.ConfigurationProperties;
import summer.core.config.DefaultValue;

/**
 * Record covering all types supported by {@code TypeConverter}.
 */
@ConfigurationProperties(prefix = "all-types")
public record AllTypesConfig(@DefaultValue("unnamed") String name, @DefaultValue("hello") String defaultedString,
		@DefaultValue("42") Integer intVal, @DefaultValue("9999999999") Long longVal,
		@DefaultValue("3.14") Double doubleVal, @DefaultValue("true") Boolean boolVal) {
}
