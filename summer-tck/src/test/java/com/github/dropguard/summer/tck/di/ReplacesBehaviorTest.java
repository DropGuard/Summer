package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.replaces.OriginalComponent;
import com.github.dropguard.summer.fixtures.di.replaces.ReplacableService;
import com.github.dropguard.summer.fixtures.di.replaces.ServiceBean;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class ReplacesBehaviorTest {

    private final BeanContainer context;

    public ReplacesBehaviorTest(BeanContainer context) {
        this.context = context;
    }

    @DualEngine
    void testReplacementHappens() {
        ReplacableService service = context.getBean(ReplacableService.class);
        assertNotNull(service);
        assertEquals(
                "replacement",
                service.serve(),
                "ReplacementComponent should replace OriginalComponent");
    }

    @DualEngine
    void testOriginalIsRemoved() {
        assertThrows(
                Exception.class,
                () -> context.getBean(OriginalComponent.class),
                "OriginalComponent should be removed after replacement");
    }

    @DualEngine
    void testConditionalReplacesConditionUnmet() {
        assertThrows(
                Exception.class,
                () ->
                        context.getBean(
                                com.github.dropguard.summer.fixtures.di.replaces.conditional
                                        .ReplacesWithConditionComponent.class));

        com.github.dropguard.summer.fixtures.di.replaces.conditional.OriginalComponent original =
                context.getBean(
                        com.github.dropguard.summer.fixtures.di.replaces.conditional
                                .OriginalComponent.class);
        assertNotNull(
                original,
                "OriginalComponent should survive when conditional replacement's condition is"
                        + " unmet");
        assertEquals("original", original.serve());
    }

    @DualEngine
    void testConfigurationReplacesCascade() {
        ServiceBean bean = context.getBean(ServiceBean.class);
        assertNotNull(bean);
        assertEquals(
                "replacement",
                bean.source(),
                "ReplacementBeanConfig's @Bean should produce the ServiceBean");
    }
}
