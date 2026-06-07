package summer.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;
import summer.core.Component;

@Component
@Logged
public class LoggingInterceptor implements MethodInterceptor {

	private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

	@Override
	public Object intercept(InterceptorChain context) throws Throwable {
		long start = System.currentTimeMillis();
		try {
			return context.proceed();
		} finally {
			long duration = System.currentTimeMillis() - start;
			log.info("[AOP] Executed {} in {}ms", context.getMethod().getName(), duration);
		}
	}
}
