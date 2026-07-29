package com.github.dropguard.summer.twitter.config;

import com.github.dropguard.summer.core.config.ConfigMapping;

/**
 * DataSource configuration bound from {@code application.yml} (prefix {@code datasource}).
 *
 * <p>Declared as a top-level {@code public} interface in its own file so both the AOT engine (which
 * generates a strong-typed {@code $$ConfigImpl} in a separate package) and the runtime engine (which
 * proxies the interface) can access it — the {@code @ConfigMapping} contract requires it.
 */
@ConfigMapping(prefix = "datasource")
public interface DataSourceProperties {

    String url();

    String username();

    String password();

    String driverClassName();
}
