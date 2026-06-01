package summer.scanner.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.aop.InvocationContext;
import summer.aop.MethodInterceptor;
import summer.core.Component;

public class ApplicationContextFailFastTest {

	@Test
	public void testAopProxyFailureCausesStartupError() {
		RuntimeApplicationContext context = new RuntimeApplicationContext();
		context.registerComponent(TestService.class);
		context.registerComponent(FailingInterceptor.class);

		assertThrows(RuntimeException.class, context::initializeBeans);
	}

	public interface ServiceInterface {
	}

	@Component
	public static class TestService implements ServiceInterface {
	}

	@Component
	public static class FailingInterceptor implements MethodInterceptor {
		@Override
		public Object intercept(InvocationContext context) throws Throwable {
			throw new RuntimeException("Simulated AOP failure");
		}
	}
}
