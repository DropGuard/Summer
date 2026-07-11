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
	public HttpParameterResolverChain resolverChain(List<HttpParameterResolver> resolvers) {
		// Sort resolvers to match original exact order required by framework
		java.util.List<HttpParameterResolver> sorted = new java.util.ArrayList<>(resolvers);
		sorted.sort(java.util.Comparator.comparingInt(r -> {
			if (r instanceof ValidatingParameterResolver) return 1;
			if (r instanceof DefaultPageResolver || r.getClass().getName().contains("Pageable")) return 2;
			if (r instanceof TypeParameterResolver) return 3;
			if (r instanceof PathParamResolver) return 4;
			if (r instanceof QueryParamResolver) return 5;
			if (r instanceof ThrowableResolver) return 6;
			return 10;
		}));
		return new HttpParameterResolverChain(sorted);
	}
}
