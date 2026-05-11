package summer.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import summer.core.annotation.Configuration;
import summer.core.annotation.Produces;
import summer.core.config.YamlConfigLoader;

@Configuration
public class DatabaseConfig {

    @Produces
    public DataSource dataSource() {
        Config config = YamlConfigLoader.loadOrDefault("application.yml", Config.class, 
            new Config("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", ""));
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.url());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(10);
        
        return new HikariDataSource(hikariConfig);
    }
    
    public record Config(String url, String username, String password) {}
}
