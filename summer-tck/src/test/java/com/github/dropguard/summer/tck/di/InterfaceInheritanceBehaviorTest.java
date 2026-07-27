package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.inheritance.BaseService;
import com.github.dropguard.summer.fixtures.di.inheritance.ExtendedService;
import com.github.dropguard.summer.fixtures.di.inheritance.ServiceClient;
import com.github.dropguard.summer.fixtures.di.inheritance.ServiceImpl;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class InterfaceInheritanceBehaviorTest {

    private final BeanContainer context;

    public InterfaceInheritanceBehaviorTest(BeanContainer context) {
        this.context = context;
    }

    @DualEngine
    void testContextStartsSuccessfully() {
        assertNotNull(context, "BeanContainer should not be null");
    }

    @DualEngine
    void testCanResolveBaseService() {
        BaseService baseService = context.getBean(BaseService.class);
        assertNotNull(baseService, "Should be able to resolve BaseService");
        assertInstanceOf(
                ServiceImpl.class, baseService, "BaseService should be resolved to ServiceImpl");
    }

    @DualEngine
    void testCanResolveExtendedService() {
        ExtendedService extendedService = context.getBean(ExtendedService.class);
        assertNotNull(extendedService, "Should be able to resolve ExtendedService");
        assertInstanceOf(
                ServiceImpl.class,
                extendedService,
                "ExtendedService should be resolved to ServiceImpl");
    }

    @DualEngine
    void testSingletonConsistency() {
        BaseService baseService = context.getBean(BaseService.class);
        ExtendedService extendedService = context.getBean(ExtendedService.class);
        assertSame(
                baseService,
                extendedService,
                "BaseService and ExtendedService should resolve to the same singleton instance");
    }

    @DualEngine
    void testDependencyInjectionWithInheritedInterface() {
        ServiceClient client = context.getBean(ServiceClient.class);
        assertNotNull(client, "ServiceClient should be instantiated");
        assertNotNull(client.getBaseService(), "ServiceClient should have BaseService injected");
        assertInstanceOf(
                ServiceImpl.class,
                client.getBaseService(),
                "Injected BaseService should be ServiceImpl");
    }
}
