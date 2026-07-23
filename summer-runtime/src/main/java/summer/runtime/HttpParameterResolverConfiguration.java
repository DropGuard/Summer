package summer.runtime;

import java.util.List;
import summer.core.RuntimeDiMarker;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;
import summer.core.config.PageableProperties;
import summer.web.CursorPageResolver;
import summer.web.DefaultPageResolver;
import summer.web.HttpParameterResolver;
import summer.web.HttpParameterResolverChain;
import summer.web.PathParamResolver;
import summer.web.QueryParamResolver;
import summer.web.ThrowableResolver;
import summer.web.TypeParameterResolver;
import summer.web.ValidatingParameterResolver;

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
	public DefaultPageResolver defaultPageResolver(PageableProperties pageableProperties) {
		return new DefaultPageResolver(pageableProperties);
	}

	@Bean
	public CursorPageResolver cursorPageResolver(PageableProperties pageableProperties) {
		return new CursorPageResolver(pageableProperties);
	}

	@Bean
	public HttpParameterResolverChain resolverChain(List<HttpParameterResolver> resolvers,
			PageableProperties pageableProperties) {
		// Explicit, reviewable resolver order. The built-in resolvers are listed
		// here in priority order (narrow claimants first among themselves, the
		// @Valid wrapper ahead of the body resolver it wraps). User-supplied
		// resolvers are appended after the built-ins, so they never silently
		// override a built-in unless declared with @Replaces (which Spring applies
		// at bean registration, before this list is built). No name sniffing, no
		// magic-number ordering — the sequence is the contract.
		java.util.List<HttpParameterResolver> builtIns = java.util.List.of(validatingResolver(),
				defaultPageResolver(pageableProperties), cursorPageResolver(pageableProperties), typeResolver(),
				pathParamResolver(), queryParamResolver(), throwableResolver());
		java.util.Set<Class<?>> builtInTypes = builtIns.stream().map(HttpParameterResolver::getClass)
				.collect(java.util.stream.Collectors.toSet());
		java.util.List<HttpParameterResolver> userResolvers = resolvers.stream()
				.filter(r -> !builtInTypes.contains(r.getClass())).toList();
		java.util.List<HttpParameterResolver> ordered = new java.util.ArrayList<>(builtIns);
		ordered.addAll(userResolvers);
		return new HttpParameterResolverChain(ordered);
	}
}
