package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.MethodReplacesBean;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class MethodReplacesBehaviorTest {

    private final BeanContainer context;

    public MethodReplacesBehaviorTest(BeanContainer context) {
        this.context = context;
    }

    @DualEngine
    void methodLevelReplacesReplacesByReturnType() {
        MethodReplacesBean bean = context.getBean(MethodReplacesBean.class);
        assertNotNull(bean, "MethodReplacesBean should be registered");
        assertEquals(
                "replaced",
                bean.getValue(),
                "MethodReplacesReplacementConfig should replace the bean");
    }
}
