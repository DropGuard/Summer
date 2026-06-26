package summer.runtime;

import org.jboss.jandex.IndexView;
import summer.core.RuntimeDiMarker;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;

@Configuration
@ConditionalOnBean(RuntimeDiMarker.class)
public class RuntimeWebConfiguration {

	@Bean
	public RuntimeRouteRegistrar routeRegistrar(HttpParameterResolverChain resolverChain) {
		// The Jandex index is loaded directly — it is the same index that the
		// RuntimeBeanContainerBuilder used during bean discovery.
		IndexView index = JandexIndexLoader.buildIndex();
		return new RuntimeRouteRegistrar(resolverChain, index);
	}

	@Bean
	public RuntimeExceptionHandlerRegistrar exceptionHandlerRegistrar(HttpParameterResolverChain resolverChain) {
		return new RuntimeExceptionHandlerRegistrar(resolverChain);
	}
}
