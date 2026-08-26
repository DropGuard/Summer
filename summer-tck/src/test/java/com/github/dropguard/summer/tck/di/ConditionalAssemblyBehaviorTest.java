package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.ConditionalBean;
import com.github.dropguard.summer.fixtures.di.TestMarker;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class ConditionalAssemblyBehaviorTest {

    @DualEngine
    void conditionalBeanPresentWhenMarkerExists(BeanContainer context) {
        assertNotNull(context.getBean(TestMarker.class), "TestMarker should be registered");
        assertNotNull(
                context.getBean(ConditionalBean.class),
                "ConditionalBean should be registered when TestMarker exists");
    }
}
