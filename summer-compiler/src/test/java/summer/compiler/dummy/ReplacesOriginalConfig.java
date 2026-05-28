package summer.compiler.dummy;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

@Configuration
public class ReplacesOriginalConfig {
	@Bean
	public DummyInterface service() {
		return new DummyService();
	}
}
