package summer.web;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * Configuration for web infrastructure beans.
 *
 * <p>
 * Provides {@link JsonBodyConverter} which handles JSON request/response
 * binding via Jackson.
 * </p>
 */
@Configuration
public class WebInfrastructureConfiguration {

	@Bean
	public JsonBodyConverter jsonBodyConverter() {
		return new JsonBodyConverter();
	}

	@Bean
	public summer.web.health.HealthRouteRegistrar healthRouteRegistrar() {
		return new summer.web.health.HealthRouteRegistrar();
	}
}
