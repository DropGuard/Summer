package summer.fixtures.di.replaces;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

@Configuration
public class OriginalBeanConfig {

	@Bean
	public ServiceBean serviceBean() {
		return new ServiceBean("original");
	}
}
