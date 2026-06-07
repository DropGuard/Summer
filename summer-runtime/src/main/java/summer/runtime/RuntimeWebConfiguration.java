package summer.runtime;

import summer.core.RuntimeDiMarker;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;

/**
 * Configuration for runtime web infrastructure beans.
 *
 * <p>
 * Provides {@link RuntimeRouteRegistrar} and
 * {@link RuntimeExceptionHandlerRegistrar} which use reflection to discover
 * routes and exception handlers at runtime. Only active when the runtime DI
 * engine is in use (i.e., {@link RuntimeDiMarker} is present).
 * </p>
 *
 * <p>
 * In AOT mode, these beans are not needed because routes and exception handlers
 * are generated statically by {@code RouteAdapterGenerator}.
 * </p>
 */
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
