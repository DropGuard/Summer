package summer.compiler.dummy;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.annotation.Replaces;

@Configuration
@Replaces(ReplacesOriginalConfig.class)
public class ReplacesReplacementConfig {
	@Bean
	public DummyInterface service() {
		return new DummyService();
	}
}
