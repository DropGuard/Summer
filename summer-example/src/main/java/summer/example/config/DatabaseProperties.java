package summer.example.config;

import summer.core.Component;

@Component
public class DatabaseProperties {
	public String getUrl() {
		return "jdbc:h2:mem:testdb_with_props";
	}

	public String getUsername() {
		return "sa";
	}
}
