package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.ContainerEngine;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDeployment;
import com.github.dropguard.summer.core.bean.MockedBean;
import java.util.Map;

@Internal
public final class AotContainer implements ContainerEngine {

    @Override
    public Engine engine() {
        return Engine.AOT;
    }

    @Override
    public BeanContainer build(
            BeanDeployment deployment,
            MockedBean[] mocks,
            Map<String, Object> overrides,
            String cacheKey,
            String className) {
        return AotEngine.buildAndCompile(deployment, cacheKey, className, mocks, overrides);
    }
}
