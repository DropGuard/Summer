package com.github.dropguard.summer.tck.aop;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.aop.metadata.ContractCache;
import com.github.dropguard.summer.fixtures.aop.metadata.MethodTaggedRecordingInterceptor;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.util.List;

/**
 * A {@code @Bean} method whose return type is an interface registers the product under that
 * interface — so the proxy built for it must implement the interface itself, not only its parent
 * interfaces, and the binding carried by the interface's methods must intercept, dual-engine.
 */
@SummerTest
public class InterfaceProductBeanTest {

    @DualEngine
    void beanMethodReturningInterfaceIsProxiedUnderItsProductType(BeanContainer context) {
        MethodTaggedRecordingInterceptor interceptor =
                context.getBean(MethodTaggedRecordingInterceptor.class);
        interceptor.clearLog();

        ContractCache cache = context.getBean(ContractCache.class);
        assertEquals("cached:k", cache.get("k"));

        assertEquals(
                List.of("record:get"),
                interceptor.getCallLog(),
                "the binding declared on the product interface's method must intercept, and "
                        + "getBean(ContractCache) must resolve the proxy");
    }
}
