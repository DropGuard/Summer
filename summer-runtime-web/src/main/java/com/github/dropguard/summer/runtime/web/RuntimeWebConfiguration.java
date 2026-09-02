package com.github.dropguard.summer.runtime.web;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.RuntimeDiMarker;
import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.runtime.InstantiatedBeans;
import com.github.dropguard.summer.web.HttpParameterResolverChain;

@Configuration
@ConditionalOnBean(RuntimeDiMarker.class)
@Internal
public class RuntimeWebConfiguration {

    @Bean
    public RuntimeRouteRegistrar routeRegistrar(
            HttpParameterResolverChain resolverChain, InstantiatedBeans instantiated) {
        return new RuntimeRouteRegistrar(resolverChain, instantiated);
    }

    @Bean
    public RuntimeExceptionHandlerRegistrar exceptionHandlerRegistrar(
            HttpParameterResolverChain resolverChain, InstantiatedBeans instantiated) {
        return new RuntimeExceptionHandlerRegistrar(resolverChain, instantiated);
    }
}
