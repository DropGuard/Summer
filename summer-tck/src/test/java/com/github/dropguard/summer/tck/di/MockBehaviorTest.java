package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dropguard.summer.fixtures.dummy.ServiceA;
import com.github.dropguard.summer.fixtures.dummy.ServiceB;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.Mock;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Verifies {@link Mock} replaces a real bean in the container and is injected
 * into dependent beans.
 *
 * <p>
 * Runs on BOTH engines via {@link DualEngine} — {@code @Mock} replacement is a
 * known dual-engine divergence risk (the AOT path must drop the mocked type
 * from codegen and register the Mockito stub), so the Runtime-only default
 * would hide any AOT break.
 * </p>
 */

@SummerTest
class MockBehaviorTest {

	private final ServiceA serviceA;
	private final ServiceB mockB;

	MockBehaviorTest(ServiceA serviceA, @Mock ServiceB mockB) {
		this.serviceA = serviceA;
		this.mockB = mockB;
	}

	@DualEngine
	void mockReplacesRealBean() {
		assertSame(mockB, serviceA.getServiceB(), "ServiceA should receive the mock instead of the real ServiceB");
	}

	@DualEngine
	void mockStubWorks() {
		when(mockB.getServiceC()).thenReturn(null);

		assertNull(serviceA.getServiceB().getServiceC(),
				"Mock stub should propagate through DI chain");
	}
}
