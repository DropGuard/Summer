package com.github.dropguard.summer.runtime.web;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.config.PageableProperties;
import com.github.dropguard.summer.web.CursorPageResolver;
import com.github.dropguard.summer.web.DefaultPageResolver;
import com.github.dropguard.summer.web.HttpParameterResolver;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import com.github.dropguard.summer.web.PathParamResolver;
import com.github.dropguard.summer.web.QueryParamResolver;
import com.github.dropguard.summer.web.ThrowableResolver;
import com.github.dropguard.summer.web.TypeParameterResolver;
import com.github.dropguard.summer.web.ValidatingParameterResolver;
import java.util.List;

/**
 * Framework configuration for HTTP parameter resolvers.
 *
 * <p>Provides the built-in {@link HttpParameterResolver} implementations and assembles them into an
 * {@link HttpParameterResolverChain}.
 *
 * <p>Engine-agnostic (NOT gated on {@code RuntimeDiMarker}): PATH/QUERY/BODY binding is inlined by
 * the AOT generator, but {@code @Pageable} deliberately defers to this chain on both engines so a
 * user {@code @Replaces} resolver behaves identically — the AOT-generated route adapter resolves
 * pageable params through the very bean assembled here.
 */
@Configuration
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

    // Explicit, reviewable built-in priority — the sequence is the contract. Narrow claimants
    // first among themselves, the @Valid wrapper ahead of the body resolver it wraps. User
    // resolvers sort after the built-ins (MAX_VALUE), so they never silently override a built-in
    // — and a @Replaces'd built-in is ABSENT from the injected list, so the replacing resolver
    // claims its slot. No name sniffing, no fresh instances.
    private static final java.util.Map<Class<?>, Integer> BUILTIN_PRIORITY =
            java.util.Map.of(
                    ValidatingParameterResolver.class, 0,
                    DefaultPageResolver.class, 1,
                    CursorPageResolver.class, 2,
                    TypeParameterResolver.class, 3,
                    PathParamResolver.class, 4,
                    QueryParamResolver.class, 5,
                    ThrowableResolver.class, 6);

    @Bean
    public HttpParameterResolverChain resolverChain(List<HttpParameterResolver> resolvers) {
        java.util.List<HttpParameterResolver> ordered =
                resolvers.stream()
                        .sorted(
                                java.util.Comparator.comparingInt(
                                        r ->
                                                BUILTIN_PRIORITY.getOrDefault(
                                                        r.getClass(), Integer.MAX_VALUE)))
                        .toList();
        return new HttpParameterResolverChain(ordered);
    }
}
