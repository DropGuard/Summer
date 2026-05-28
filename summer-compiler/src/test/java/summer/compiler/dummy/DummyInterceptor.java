package summer.compiler.dummy;

import summer.aop.Intercepts;
import summer.aop.InvocationContext;
import summer.aop.MethodInterceptor;
import summer.core.Component;

@Component
@Intercepts(annotations = DummyAnnotation.class)
public class DummyInterceptor implements MethodInterceptor {
	public Object intercept(InvocationContext ctx) throws Throwable {
		return ctx.proceed();
	}
}
