package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.dummy.EchoPortConsumer;
import com.github.dropguard.summer.fixtures.dummy.RealEchoService;
import com.github.dropguard.summer.fixtures.dummy.ServiceA;
import com.github.dropguard.summer.fixtures.dummy.ServiceB;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.Mock;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Verifies {@link Mock} replaces a real bean in the container and is injected into dependent beans.
 *
 * <p>Runs on BOTH engines via {@link DualEngine} — {@code @Mock} replacement is a known dual-engine
 * divergence risk (the AOT path must drop the mocked type from codegen and register the Mockito
 * stub), so the Runtime-only default would hide any AOT break.
 *
 * <p>Assertions read beans from the per-engine {@link BeanContainer} passed as a method parameter
 * (the test instance itself is always built by the RUNTIME container), so the AOT invocation
 * genuinely asserts AOT mock injection.
 */
@SummerTest
class MockBehaviorTest {

    MockBehaviorTest(ServiceA serviceA, @Mock ServiceB mockB, @Mock RealEchoService mockEcho) {
        // Constructor params drive the container build (@Mock registration). Assertions
        // use the per-engine container passed to each @DualEngine method instead.
    }

    @DualEngine
    void mockReplacesRealBean(BeanContainer container) {
        ServiceA serviceA = container.getBean(ServiceA.class);
        ServiceB mockB = container.getBean(ServiceB.class);
        assertSame(
                mockB,
                serviceA.getServiceB(),
                "ServiceA should receive the mock instead of the real ServiceB");
    }

    @DualEngine
    void mockStubWorks(BeanContainer container) {
        ServiceA serviceA = container.getBean(ServiceA.class);
        ServiceB mockB = container.getBean(ServiceB.class);
        when(mockB.getServiceC()).thenReturn(null);

        assertNull(
                serviceA.getServiceB().getServiceC(),
                "Mock stub should propagate through DI chain");
    }

    // --- S-09a: a @Mock declared on the CONCRETE type must reach consumers that inject by a
    // TRANSITIVE interface of that type. Both engines resolve through BeanContainer.getBean's
    // assignability scan; exact-key peeking silently injected null on the AOT side before, and
    // the shared resolver rejected the build outright (isMocked knew only the target + its
    // DIRECT interfaces). ---

    @DualEngine
    void mockDeclaredOnConcreteIsResolvedThroughTransitiveInterface(BeanContainer container) {
        RealEchoService mock = container.getBean(RealEchoService.class);
        when(mock.send(anyString())).thenReturn("stubbed-port");

        EchoPortConsumer consumer = container.getBean(EchoPortConsumer.class);
        assertEquals(
                "stubbed-port",
                consumer.send("y"),
                "consumer injecting the transitive interface must receive the MOCK — "
                        + "the real bean was removed from the universe by @Mock");
    }
}
