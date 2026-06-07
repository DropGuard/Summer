package summer.fixtures.di;

import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;

/**
 * Configuration that conditionally registers {@link ConditionalBean}
 * based on the presence of {@link TestMarker}.
 */
@Configuration
public class ConditionalTestConfig {

	@Bean
	public TestMarker testMarker() {
		return new TestMarker();
	}

	@Bean
	@ConditionalOnBean(TestMarker.class)
	public ConditionalBean conditionalBean() {
		return new ConditionalBean();
	}
}
