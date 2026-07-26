package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.RuntimeDiMarker;
import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.web.HttpParameterResolverChain;

@Configuration
@ConditionalOnBean(RuntimeDiMarker.class)
public class RuntimeWebConfiguration {

	@Bean
	public RuntimeRouteRegistrar routeRegistrar(HttpParameterResolverChain resolverChain) {
		return new RuntimeRouteRegistrar(resolverChain);
	}

	@Bean
	public RuntimeExceptionHandlerRegistrar exceptionHandlerRegistrar(HttpParameterResolverChain resolverChain) {
		return new RuntimeExceptionHandlerRegistrar(resolverChain);
	}
}
