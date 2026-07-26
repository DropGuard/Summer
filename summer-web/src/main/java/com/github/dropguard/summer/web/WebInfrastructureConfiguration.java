package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

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
	public com.github.dropguard.summer.web.health.HealthRouteRegistrar healthRouteRegistrar() {
		return new com.github.dropguard.summer.web.health.HealthRouteRegistrar();
	}
}
