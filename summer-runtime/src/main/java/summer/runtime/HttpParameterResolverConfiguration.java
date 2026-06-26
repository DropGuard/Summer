package summer.runtime;

import java.util.List;
import summer.core.RuntimeDiMarker;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;

/**
 * Framework configuration for HTTP parameter resolvers.
 *
 * <p>
 * Provides the built-in {@link HttpParameterResolver} implementations and
 * assembles them into an {@link HttpParameterResolverChain}.
 * </p>
 *
 * <p>
 * Only active when the runtime DI engine is in use (i.e.,
 * {@link RuntimeDiMarker} is present). In AOT mode, parameter binding is
 * generated inline by {@code RouteAdapterGenerator}.
 * </p>
 */
@Configuration
@ConditionalOnBean(RuntimeDiMarker.class)
public class HttpParameterResolverConfiguration {

	@Bean
	public ValidatingParameterResolver validatingResolver() {
		return new ValidatingParameterResolver();
	}

	@Bean
	public TypeParameterResolver typeResolver() {
		return new TypeParameterResolver();
	}

	@Bean
	public PathParamResolver pathParamResolver() {
		return new PathParamResolver();
	}

	@Bean
	public QueryParamResolver queryParamResolver() {
		return new QueryParamResolver();
	}

	@Bean
	public ThrowableResolver throwableResolver() {
		return new ThrowableResolver();
	}

	@Bean
	public HttpParameterResolverChain resolverChain(ValidatingParameterResolver validatingResolver,
			PageableResolver pageableResolver, TypeParameterResolver typeResolver, PathParamResolver pathParamResolver,
			QueryParamResolver queryParamResolver, ThrowableResolver throwableResolver) {
		return new HttpParameterResolverChain(List.of(validatingResolver, pageableResolver, typeResolver,
				pathParamResolver, queryParamResolver, throwableResolver));
	}
}
