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
 * <li>{@link ReflectionParameterResolver} — @PathParam, @QueryParam,
 * HttpContext, Request, Throwable</li>
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
	public ReflectionParameterResolver reflectionResolver() {
		return new ReflectionParameterResolver();
	}

	@Bean
	public HttpParameterResolverChain resolverChain(ValidatingParameterResolver validatingResolver,
			PageableResolver pageableResolver, ReflectionParameterResolver reflectionResolver) {
		return new HttpParameterResolverChain(List.of(validatingResolver, pageableResolver, reflectionResolver));
	}
}
