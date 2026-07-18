package summer.runtime;

import summer.core.RuntimeDiMarker;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;
import summer.core.config.PageableProperties;
import summer.web.DefaultPageResolver;

/**
 * Configuration for pagination support.
 *
 * <p>
 * Provides {@link DefaultPageResolver} with configurable defaults.
 * </p>
 */
@Configuration
@ConditionalOnBean(RuntimeDiMarker.class)
public class PageableConfiguration {

	@Bean
	public DefaultPageResolver defaultPageResolver(PageableProperties props) {
		return new DefaultPageResolver(props);
	}
}
