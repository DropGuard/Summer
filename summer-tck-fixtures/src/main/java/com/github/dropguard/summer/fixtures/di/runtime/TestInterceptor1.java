package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.aop.Interceptor;
import com.github.dropguard.summer.aop.InterceptorChain;
import com.github.dropguard.summer.aop.MethodInterceptor;

@TestIntercepted
@Interceptor
public class TestInterceptor1 implements MethodInterceptor {
    @Override
    public Object intercept(InterceptorChain chain) throws Throwable {
        return "[1]" + chain.proceed();
    }
}
