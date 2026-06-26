package summer.fixtures.di.runtime;

import summer.aop.Interceptor;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;

@TestIntercepted
@Interceptor
public class TestInterceptor2 implements MethodInterceptor {
	@Override
	public Object intercept(InterceptorChain chain) throws Throwable {
		return "[2]" + chain.proceed();
	}
}
