package summer.fixtures.di;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.annotation.Replaces;

/**
 * Replacement configuration that uses method-level @Replaces to replace the
 * bean by return type.
 */
@Configuration
public class MethodReplacesReplacementConfig {

	@Bean
	@Replaces(MethodReplacesBean.class)
	public MethodReplacesBean methodReplacesBean() {
		return new MethodReplacesBean("replaced");
	}
}
