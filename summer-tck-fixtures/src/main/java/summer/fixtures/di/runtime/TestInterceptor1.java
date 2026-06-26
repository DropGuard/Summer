package summer.fixtures.di.runtime;

import summer.aop.Interceptor;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;

@TestIntercepted
@Interceptor
public class TestInterceptor1 implements MethodInterceptor {
	@Override
	public Object intercept(InterceptorChain chain) throws Throwable {
		return "[1]" + chain.proceed();
	}
}
