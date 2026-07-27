package com.github.dropguard.summer.issuetracker.config;

import com.github.dropguard.summer.core.config.ConfigMapping;

@ConfigMapping(prefix = "datasource")
public interface DataSourceProperties {

    String url();

    String username();

    String password();

    String driverClassName();
}
