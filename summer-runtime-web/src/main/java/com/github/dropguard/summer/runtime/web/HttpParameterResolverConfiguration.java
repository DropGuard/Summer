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

    @Bean
    public HttpParameterResolverChain resolverChain(
            List<HttpParameterResolver> resolvers, PageableProperties pageableProperties) {
        // Explicit, reviewable resolver order. The built-in resolvers are listed
        // here in priority order (narrow claimants first among themselves, the
        // @Valid wrapper ahead of the body resolver it wraps). User-supplied
        // resolvers are appended after the built-ins, so they never silently
        // override a built-in unless declared with @Replaces (which Spring applies
        // at bean registration, before this list is built). No name sniffing, no
        // magic-number ordering — the sequence is the contract.
        java.util.List<HttpParameterResolver> builtIns =
                java.util.List.of(
                        validatingResolver(),
                        defaultPageResolver(pageableProperties),
                        cursorPageResolver(pageableProperties),
                        typeResolver(),
                        pathParamResolver(),
                        queryParamResolver(),
                        throwableResolver());
        java.util.Set<Class<?>> builtInTypes =
                builtIns.stream()
                        .map(HttpParameterResolver::getClass)
                        .collect(java.util.stream.Collectors.toSet());
        java.util.List<HttpParameterResolver> userResolvers =
                resolvers.stream().filter(r -> !builtInTypes.contains(r.getClass())).toList();
        java.util.List<HttpParameterResolver> ordered = new java.util.ArrayList<>(builtIns);
        ordered.addAll(userResolvers);
        return new HttpParameterResolverChain(ordered);
    }
}
