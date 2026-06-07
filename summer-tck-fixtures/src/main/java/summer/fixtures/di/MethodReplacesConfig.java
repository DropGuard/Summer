package summer.fixtures.di;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * Original configuration that provides a bean for method-level @Replaces tests.
 */
@Configuration
public class MethodReplacesConfig {

	@Bean
	public MethodReplacesBean methodReplacesBean() {
		return new MethodReplacesBean("original");
	}
}
