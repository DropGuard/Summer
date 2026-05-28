package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.dummy.ServiceA;
import summer.tck.dummy.ServiceB;
import summer.tck.dummy.ServiceC;

public abstract class AbstractDependencyInjectionTCK {

	protected ApplicationContext context;

	protected abstract ApplicationContext createAndInitializeContext();

	protected ApplicationContext getContext() {
		if (context == null) {
			context = createAndInitializeContext();
		}
		return context;
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.destroy();
			context = null;
		}
	}

	@Test
	void testContextStartsSuccessfully() {
		assertNotNull(getContext(), "ApplicationContext should not be null");
	}

	@Test
	void testSingletonUniqueness() {
		ApplicationContext ctx = getContext();
		ServiceC c1 = ctx.getBean(ServiceC.class);
		ServiceC c2 = ctx.getBean(ServiceC.class);
		assertNotNull(c1);
		assertSame(c1, c2, "Multiple calls to getBean should return the same singleton instance");
	}

	@Test
	void testDependencyResolution() {
		ApplicationContext ctx = getContext();

		ServiceA a = ctx.getBean(ServiceA.class);
		ServiceB b = ctx.getBean(ServiceB.class);
		ServiceC c = ctx.getBean(ServiceC.class);

		assertNotNull(a);
		assertNotNull(b);
		assertNotNull(c);

		assertSame(b, a.getServiceB(), "ServiceA should be injected with the singleton ServiceB");
		assertSame(c, b.getServiceC(), "ServiceB should be injected with the singleton ServiceC");
	}
}
