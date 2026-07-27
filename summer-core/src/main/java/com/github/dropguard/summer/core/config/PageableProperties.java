package com.github.dropguard.summer.core.config;

/**
 * Configuration properties for pagination defaults.
 *
 * <p>Bound from {@code application.yml} under the {@code pageable} prefix.
 *
 * <p>Example YAML:
 *
 * <pre>{@code
 * pageable:
 *   default-page: 0
 *   default-size: 20
 * }</pre>
 *
 * @param defaultPage zero-based page index
 * @param defaultSize number of items per page
 */
@ConfigMapping(prefix = "pageable")
public interface PageableProperties {

    @WithDefault("0")
    Integer defaultPage();

    @WithDefault("20")
    Integer defaultSize();
}
