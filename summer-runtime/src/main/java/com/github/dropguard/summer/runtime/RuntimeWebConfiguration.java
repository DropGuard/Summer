package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.RuntimeDiMarker;
import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnBean(RuntimeDiMarker.class)
public class RuntimeWebConfiguration {

    @Bean
    public RuntimeRouteRegistrar routeRegistrar(HttpParameterResolverChain resolverChain) {
        return new RuntimeRouteRegistrar(resolverChain);
    }

    @Bean
    public RuntimeExceptionHandlerRegistrar exceptionHandlerRegistrar(
            HttpParameterResolverChain resolverChain, BeanContainer.Builder builder) {
        Map<String, List<BeanDefinition.ExceptionHandlerEntry>> handlers =
                builder.handlerMetadata();
        return new RuntimeExceptionHandlerRegistrar(resolverChain, handlers);
    }
}
