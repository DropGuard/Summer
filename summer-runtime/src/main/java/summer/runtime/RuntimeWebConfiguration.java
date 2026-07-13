package summer.runtime;

import summer.core.RuntimeDiMarker;
import org.jboss.jandex.IndexView;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;

@Configuration
@ConditionalOnBean(RuntimeDiMarker.class)
public class RuntimeWebConfiguration {

	@Bean
	public RuntimeRouteRegistrar routeRegistrar(IndexView index, HttpParameterResolverChain resolverChain) {
		return new RuntimeRouteRegistrar(resolverChain, index);
	}

	@Bean
	public RuntimeExceptionHandlerRegistrar exceptionHandlerRegistrar(
			HttpParameterResolverChain resolverChain) {
		return new RuntimeExceptionHandlerRegistrar(resolverChain);
	}
}
