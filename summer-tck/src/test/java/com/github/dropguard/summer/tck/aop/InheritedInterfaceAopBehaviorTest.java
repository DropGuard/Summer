package com.github.dropguard.summer.tck.aop;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import com.github.dropguard.summer.fixtures.aop.RecordingInterceptor;
import com.github.dropguard.summer.fixtures.aop.inherited.EchoApi;
import com.github.dropguard.summer.fixtures.aop.inherited.MarkerApi;
import com.github.dropguard.summer.fixtures.aop.inherited.PlainEchoChild;
import com.github.dropguard.summer.fixtures.aop.inherited.TaggedEchoChild;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.util.List;

/**
 * Interfaces inherited through the CLASS hierarchy (abstract base pattern) must participate in
 * discovery, proxying and binding — on both engines. Before the superclass-chain fix these beans
 * silently ran raw: no proxy, no interception, no error.
 *
 * <p>Resolution-shape notes (the AOP lookup contract, one bean, one form): a bound bean exists in
 * the lookup plane ONLY as its proxy — under its unique interface keys and as the value of its
 * concrete-class key (which typed lookups can never resolve, since the proxy is not an instance of
 * the concrete class). Collection scans therefore see exactly ONE entry per bean, always the
 * intercepting proxy — including beans whose ONLY interface is shared (their proxy surfaces via the
 * concrete-class key's value).
 */
@SummerTest
public class InheritedInterfaceAopBehaviorTest {

    @DualEngine
    void inheritedBindingInterceptsThroughUniqueDirectInterface(BeanContainer context) {
        RecordingInterceptor interceptor = context.getBean(RecordingInterceptor.class);
        interceptor.clearLog();

        // Unique impl of MarkerApi -> the interface key carries the PROXY.
        MarkerApi marker = context.getBean(MarkerApi.class);
        assertInstanceOf(EchoApi.class, marker, "the proxy must expose the inherited interface");

        assertEquals("base:y", ((EchoApi) marker).echo("y"));
        assertEquals(
                List.of("before:echo", "after:echo"),
                interceptor.getCallLog(),
                "inherited-interface bindings must intercept alongside the direct interface");
    }

    // Historical note: before the one-bean-one-form contract, a bean whose ONLY interfaces were
    // shared multi-impl ones (like PlainEchoChild) got proxied but the proxy had NO home in the
    // lookup plane — collection injection silently received its raw instance. Under the current
    // contract the proxy is always present (as its concrete-class key's value), so such beans are
    // intercepted everywhere; the test above pins exactly that.

    @DualEngine
    void inheritedInterfaceParticipatesInTypeResolution(BeanContainer context) {
        RecordingInterceptor interceptor = context.getBean(RecordingInterceptor.class);

        // Two beans share the inherited interface: single-type resolution must fail loudly
        // (Spring-consistent) rather than hand out an arbitrary candidate.
        assertThrows(
                AmbiguousBeanException.class,
                () -> context.getBean(EchoApi.class),
                "inherited interfaces participate in resolution — two candidates must "
                        + "be an ambiguity, not silence");

        // AOP lookup contract (one bean, one form): collection resolution is HOMOGENEOUS —
        // exactly one entry per bean, and for bound beans that entry is the proxy. Before the
        // contract this list mixed raw twins with proxies (or dropped homeless proxies
        // entirely); now both children surface exactly once, intercepted.
        List<EchoApi> echoes = context.getBeans(EchoApi.class);
        assertEquals(2, echoes.size(), "one entry per bean — no raw/proxy twins");
        assertNotSame(echoes.get(0), echoes.get(1), "the two entries are distinct beans");
        for (EchoApi echo : echoes) {
            interceptor.clearLog();
            assertEquals("base:x", echo.echo("x"));
            assertEquals(
                    List.of("before:echo", "after:echo"),
                    interceptor.getCallLog(),
                    "every resolved entry must be the intercepting proxy");
            assertNotEquals(PlainEchoChild.class, echo.getClass(), "never the raw instance");
            assertNotEquals(TaggedEchoChild.class, echo.getClass(), "never the raw instance");
        }

        // Concrete-typed lookups of BOUND beans fail loudly (no escape hatch).
        assertThrows(
                NoSuchBeanException.class,
                () -> context.getBean(PlainEchoChild.class),
                "bound bean is reachable only through its interfaces");
    }
}
