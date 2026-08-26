package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.generic.GenericService;
import com.github.dropguard.summer.fixtures.di.generic.GenericServiceClient;
import com.github.dropguard.summer.fixtures.di.generic.StringServiceImpl;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class GenericInterfaceBehaviorTest {

    @DualEngine
    void testContextStartsSuccessfully(BeanContainer context) {
        assertNotNull(context, "BeanContainer should not be null");
    }

    @DualEngine
    void testCanResolveGenericService(BeanContainer context) {
        GenericService<?> service = context.getBean(GenericService.class);
        assertNotNull(service, "Should be able to resolve GenericService (raw type)");
        assertInstanceOf(
                StringServiceImpl.class,
                service,
                "GenericService should be resolved to StringServiceImpl");
    }

    @DualEngine
    void testCanResolveStringServiceImpl(BeanContainer context) {
        StringServiceImpl service = context.getBean(StringServiceImpl.class);
        assertNotNull(service, "Should be able to resolve StringServiceImpl");
    }

    @DualEngine
    void testSingletonConsistency(BeanContainer context) {
        GenericService<?> genericService = context.getBean(GenericService.class);
        StringServiceImpl stringService = context.getBean(StringServiceImpl.class);
        assertSame(
                genericService,
                stringService,
                "GenericService and StringServiceImpl should resolve to the same singleton"
                        + " instance");
    }

    @DualEngine
    void testDependencyInjectionWithGenericInterface(BeanContainer context) {
        GenericServiceClient client = context.getBean(GenericServiceClient.class);
        assertNotNull(client, "GenericServiceClient should be instantiated");
        assertNotNull(
                client.getService(), "GenericServiceClient should have GenericService injected");
        assertInstanceOf(
                StringServiceImpl.class,
                client.getService(),
                "Injected GenericService should be StringServiceImpl");
    }
}
