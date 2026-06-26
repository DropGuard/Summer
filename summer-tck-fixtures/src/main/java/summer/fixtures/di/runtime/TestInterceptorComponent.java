package summer.fixtures.di.runtime;

import summer.aop.Interceptor;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;
import summer.core.Component;

@TestIntercepted
@Component
@Interceptor
public class TestInterceptorComponent implements MethodInterceptor {
	@Override
	public Object intercept(InterceptorChain chain) throws Throwable {
		return "[proxied] " + chain.proceed();
	}
}
