package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.fixtures.dummy.ServiceA;
import com.github.dropguard.summer.fixtures.dummy.ServiceB;
import com.github.dropguard.summer.fixtures.dummy.ServiceC;
import com.github.dropguard.summer.test.Testing;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Phase 0 of the DI build-chain redesign: confirm (or refute) the AOT {@code build(MockedBean[])}
 * dual-method generation path on a CLEAN seeded universe.
 *
 * <p>The AOT engine keeps two generated entry points: the production-only {@code build(Object...)}
 * (single method, emitted when mocks are null) and the test-only typed {@code build(MockedBean[])}
 * (emitted when mocks are non-null). The typed path is the one Quarkus-aligned test containers and
 * {@code @Mock} exercise — but it was never covered by any existing test that uses the
 * pre-generated single-method context from the maven plugin. So this test forces it: {@link
 * Testing#buildNarrowAot} passes a NON-null (empty) mocks array, which makes {@code
 * AotContextGenerator} emit {@code build(MockedBean[])} instead of {@code build(Object...)}.
 *
 * <p>The universe is deliberately the cleanest possible DI graph — {@code ServiceA -> ServiceB ->
 * ServiceC} — built via a narrow index that contains ONLY those three classes plus their transitive
 * closure. This avoids the whole-universe test deployment, which carries web controllers,
 * {@code @Replaces} targets, and route-validation fixtures that fail discovery BEFORE generation is
 * ever reached and would masquerade as a generator bug. If this test is green, the dual-method path
 * is fine and the earlier "broken" symptom was discovery-stage noise, not a codegen defect.
 */
class AotDualMethodBehaviorTest {

    @Test
    void dualMethodPathGeneratesCompilesAndWiresCleanUniverse() {
        Class<?>[] seeds = {ServiceA.class, ServiceB.class, ServiceC.class};

        // Non-null empty mocks list => AOT emits the typed build(MockedBean[])
        // channel, not the production build(Object...). This is the isolated path
        // under verification.
        BeanContainer container = Testing.buildNarrowAot(seeds, List.of(), Map.of());

        assertNotNull(container, "AOT build(MockedBean[]) path must return a container");

        // The full dependency chain must resolve through generated code.
        ServiceA a = container.getBean(ServiceA.class);
        ServiceB b = container.getBean(ServiceB.class);
        ServiceC c = container.getBean(ServiceC.class);

        assertNotNull(a, "ServiceA should be wired by the AOT dual-method context");
        assertNotNull(b, "ServiceB should be wired by the AOT dual-method context");
        assertNotNull(c, "ServiceC should be wired by the AOT dual-method context");

        assertSame(b, a.getServiceB(), "ServiceA must receive the generated ServiceB");
        assertSame(c, b.getServiceC(), "ServiceB must receive the generated ServiceC");
        assertEquals("Hello from C", c.getMessage());
    }

    /**
     * The non-empty mocks branch must still generate, compile, and load a working container. This
     * exercises {@code build(MockedBean[])} with a real (non-null) mock array — proving the typed
     * channel is not just syntactically valid but end-to-end runnable — while the empty-mocks test
     * above proves the graph wires through. Mock *replacement* semantics (the stub standing in for
     * the real bean) is a separate concern verified by {@code MockBehaviorTest} through the full
     * {@code @Mock} pipeline, not the narrow direct-AOT entry used here.
     */
    @Test
    void dualMethodPathWithMockArrayGeneratesCompilesAndLoads() {
        Class<?>[] seeds = {ServiceA.class, ServiceB.class, ServiceC.class};

        ServiceC mockC = mock(ServiceC.class);
        when(mockC.getMessage()).thenReturn("mocked C");
        MockedBean mocked = MockedBean.of(ServiceC.class, mockC);
        BeanContainer container = Testing.buildNarrowAot(seeds, List.of(mocked), Map.of());

        assertNotNull(
                container,
                "AOT build(MockedBean[]) path with a non-empty mock array must return a container");
        // The non-mocked beans must still resolve — the mock only targets ServiceC.
        assertNotNull(
                container.getBean(ServiceA.class),
                "ServiceA must still wire under the mock-array path");
        assertNotNull(
                container.getBean(ServiceB.class),
                "ServiceB must still wire under the mock-array path");
    }
}
