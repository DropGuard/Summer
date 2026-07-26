package summer.issuetracker.config;

import summer.core.config.ConfigurationProperties;

@ConfigurationProperties(prefix = "summer.datasource")
public record DataSourceProperties(String url, String username, String password, String driverClassName) {
}
