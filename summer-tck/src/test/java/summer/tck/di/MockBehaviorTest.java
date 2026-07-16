package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import summer.fixtures.dummy.ServiceA;
import summer.fixtures.dummy.ServiceB;
import summer.test.annotation.Mock;
import summer.test.annotation.SummerTest;

/**
 * Verifies {@link Mock} replaces a real bean in the container and is injected
 * into dependent beans.
 */
@SummerTest(modules = "summer-tck-fixtures")
class MockBehaviorTest {

	private final ServiceA serviceA;
	private final ServiceB mockB;

	MockBehaviorTest(ServiceA serviceA, @Mock ServiceB mockB) {
		this.serviceA = serviceA;
		this.mockB = mockB;
	}

	@Test
	void mockReplacesRealBean() {
		assertSame(mockB, serviceA.getServiceB(), "ServiceA should receive the mock instead of the real ServiceB");
	}

	@Test
	void mockStubWorks() {
		when(mockB.getServiceC()).thenReturn(null);

		assertNull(serviceA.getServiceB().getServiceC(),
				"Mock stub should propagate through DI chain");
	}
}
