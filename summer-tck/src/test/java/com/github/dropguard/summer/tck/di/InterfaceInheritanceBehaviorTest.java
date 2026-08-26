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

    @DualEngine
    void testContextStartsSuccessfully(BeanContainer context) {
        assertNotNull(context, "BeanContainer should not be null");
    }

    @DualEngine
    void testCanResolveBaseService(BeanContainer context) {
        BaseService baseService = context.getBean(BaseService.class);
        assertNotNull(baseService, "Should be able to resolve BaseService");
        assertInstanceOf(
                ServiceImpl.class, baseService, "BaseService should be resolved to ServiceImpl");
    }

    @DualEngine
    void testCanResolveExtendedService(BeanContainer context) {
        ExtendedService extendedService = context.getBean(ExtendedService.class);
        assertNotNull(extendedService, "Should be able to resolve ExtendedService");
        assertInstanceOf(
                ServiceImpl.class,
                extendedService,
                "ExtendedService should be resolved to ServiceImpl");
    }

    @DualEngine
    void testSingletonConsistency(BeanContainer context) {
        BaseService baseService = context.getBean(BaseService.class);
        ExtendedService extendedService = context.getBean(ExtendedService.class);
        assertSame(
                baseService,
                extendedService,
                "BaseService and ExtendedService should resolve to the same singleton instance");
    }

    @DualEngine
    void testDependencyInjectionWithInheritedInterface(BeanContainer context) {
        ServiceClient client = context.getBean(ServiceClient.class);
        assertNotNull(client, "ServiceClient should be instantiated");
        assertNotNull(client.getBaseService(), "ServiceClient should have BaseService injected");
        assertInstanceOf(
                ServiceImpl.class,
                client.getBaseService(),
                "Injected BaseService should be ServiceImpl");
    }
}
