package summer.core.config;
/**
 * Configuration properties for pagination defaults.
 *
 * <p>
 * Bound from {@code application.yml} under the {@code summer.pageable} prefix.
 * </p>
 *
 * <p>
 * Example YAML:
 * </p>
 *
 * <pre>{@code
 * summer:
 *   pageable:
 *     default-page: 0
 *     default-size: 20
 * }</pre>
 *
 * @param defaultPage
 *            zero-based page index
 * @param defaultSize
 *            number of items per page
 */
@ConfigurationProperties(prefix = "summer.pageable")
public record PageableProperties(@DefaultValue("0") Integer defaultPage, @DefaultValue("20") Integer defaultSize) {
}
