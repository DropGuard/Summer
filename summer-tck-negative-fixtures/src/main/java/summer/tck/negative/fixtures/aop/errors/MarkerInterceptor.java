package summer.tck.negative.fixtures.aop.errors;

import java.util.List;
import summer.aop.Interceptor;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;
import summer.core.Component;

/**
 * Local interceptor bound to {@link AopMarker}. Paired with
 * {@link NoInterfaceBoundBean} so the narrow {@code @SummerTest(classes=...)}
 * path indexes both the binding and its interceptor, which makes the broken
 * bean actually need a proxy -- and therefore trip the no-interface fail-fast.
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
