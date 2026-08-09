package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.tck.invisible.fixtures.closeorder.CloseOrderNoClose;
import com.github.dropguard.summer.tck.invisible.fixtures.closeorder.CloseOrderProducerConfig;
import com.github.dropguard.summer.tck.invisible.fixtures.closeorder.CloseOrderProduct;
import com.github.dropguard.summer.tck.invisible.fixtures.closeorder.CloseOrderRecorder;
import com.github.dropguard.summer.tck.invisible.fixtures.closeorder.CloseOrderRegular;
import com.github.dropguard.summer.test.TestContainer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The CDI teardown contract on BOTH engines: the container owns each bean once (beans, not type
 * keys) and closes {@code @Bean} products before non-products (the producer-destruction rule — the
 * product's close may access the producer's still-alive state). No topological guarantee beyond
 * that. The AOT invocation runs the same scenario through the generated container — the shared
 * {@code BeanContainer.close} must behave identically on both engines.
 */
public class BeanCloseOrderDualEngineTest {

    @Test
    void productsCloseBeforeRegularsOnBothEngines() throws Exception {
        for (Engine engine : Engine.values()) {
            CloseOrderRecorder.clear();
            BeanContainer context =
                    TestContainer.builder()
                            .testClass(getClass())
                            .engine(engine)
                            .beans(
                                    CloseOrderProducerConfig.class,
                                    CloseOrderProduct.class,
                                    CloseOrderRegular.class,
                                    CloseOrderNoClose.class)
                            .build();
            context.close();
            List<String> closed = CloseOrderRecorder.closed();
            assertEquals(
                    "product",
                    closed.get(0),
                    engine + " invocation: the @Bean product closes first");
            assertTrue(
                    closed.indexOf("producer") > closed.indexOf("product"),
                    engine
                            + " invocation: the product closes before its producer (the CDI"
                            + " producer-destruction rule)");
            assertEquals(
                    3,
                    closed.size(),
                    engine
                            + " invocation: product + producer + regular each close exactly once;"
                            + " the non-AutoCloseable CloseOrderNoClose is skipped — the regular's"
                            + " position after the product is unspecified");
        }
    }
}
