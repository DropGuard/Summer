package summer.runtime;

import summer.core.RuntimeDiMarker;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;
import summer.core.config.PageableProperties;

/**
 * Configuration for pagination support.
 *
 * <p>
 * Provides {@link PageableResolver} with configurable defaults.
 * </p>
 */
@Configuration
@ConditionalOnBean(RuntimeDiMarker.class)
public class PageableConfiguration {

	@Bean
	public PageableResolver pageableResolver(PageableProperties props) {
		return new PageableResolver(props);
	}
}
