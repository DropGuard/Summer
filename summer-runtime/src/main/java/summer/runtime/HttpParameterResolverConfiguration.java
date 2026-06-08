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
 * assembles them into an {@link HttpParameterResolverChain}. The resolver order
 * matches the {@code @Bean} method declaration order.
 * </p>
 *
 * <p>
 * {@link PageableResolver} is provided by {@link PageableConfiguration} with
 * configurable defaults.
 * </p>
 *
 * <p>
 * Only active when the runtime DI engine is in use (i.e.,
 * {@link RuntimeDiMarker} is present). In AOT mode, parameter binding is
 * generated inline by {@code RouteAdapterGenerator}.
 * </p>
 *
 * <p>
 * Resolution order:
 * </p>
 * <ol>
 * <li>{@link ValidatingParameterResolver} — @Valid annotated parameters</li>
 * <li>{@link PageableResolver} — Pageable parameters</li>
 * <li>{@link TypeParameterResolver} — HttpContext, Request</li>
 * <li>{@link PathParamResolver} — @PathParam</li>
 * <li>{@link QueryParamResolver} — @QueryParam</li>
 * <li>{@link ThrowableResolver} — Throwable (for @ExceptionHandler)</li>
 * </ol>
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
