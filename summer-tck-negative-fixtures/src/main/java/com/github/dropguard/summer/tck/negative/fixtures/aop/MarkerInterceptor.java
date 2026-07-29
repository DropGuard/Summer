package com.github.dropguard.summer.tck.negative.fixtures.aop;

import com.github.dropguard.summer.aop.Interceptor;
import com.github.dropguard.summer.aop.InterceptorChain;
import com.github.dropguard.summer.aop.MethodInterceptor;
import com.github.dropguard.summer.core.Component;
import java.util.List;

/**
 * Local interceptor bound to {@link AopMarker}. Paired with {@link NoInterfaceBoundBean} so the
 * narrow {@code @SummerTest(classes=...)} path indexes both the binding and its interceptor, which
 * makes the broken bean actually need a proxy -- and therefore trip the no-interface fail-fast.
 */
@Component
@Interceptor
@AopMarker
public class MarkerInterceptor implements MethodInterceptor {

    @Override
    public Object intercept(InterceptorChain chain) throws Throwable {
        return chain.proceed();
    }

    public List<String> recorded() {
        return List.of();
    }
}
