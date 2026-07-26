package com.github.dropguard.summer.issuetracker.config;

import com.github.dropguard.summer.core.config.ConfigurationProperties;

@ConfigurationProperties(prefix = "com.github.dropguard.summer.datasource")
public record DataSourceProperties(String url, String username, String password, String driverClassName) {
}
