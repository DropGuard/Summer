package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.aop.Interceptor;
import com.github.dropguard.summer.aop.InterceptorChain;
import com.github.dropguard.summer.aop.MethodInterceptor;
import com.github.dropguard.summer.core.Component;

@TestIntercepted
@Component
@Interceptor
public class TestInterceptorComponent implements MethodInterceptor {
    @Override
    public Object intercept(InterceptorChain chain) throws Throwable {
        return "[proxied] " + chain.proceed();
    }
}
