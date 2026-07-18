package summer.runtime;

import summer.core.RuntimeDiMarker;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;
import summer.web.HttpParameterResolverChain;

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
