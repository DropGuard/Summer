package summer.tck.di.root;

import summer.core.config.ConfigurationProperties;

/**
 * Test fixture: @ConfigurationProperties with empty prefix — binds the entire
 * YAML root.
 *
 * <p>
 * The YAML has a flat {@code root:} section with {@code host} and {@code port}.
 * With {@code prefix = ""}, the binder extracts the root map; Jackson maps the
 * {@code root} key to this record's {@code root} component. Other top-level
 * keys ({@code app}, {@code server}, etc.) are ignored as unknown fields.
 * </p>
 */
@ConfigurationProperties(prefix = "")
public record RootProperties(Root root) {

	/**
	 * Nested record matching the {@code root:} YAML section.
	 */
	public record Root(String host, Integer port) {
	}
}
