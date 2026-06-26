package summer.fixtures.di.runtime;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

@Configuration
public class ProducerConfig {
	@Bean
	public ProducedBean producedBean() {
		return new ProducedBean("produced-value");
	}
}
