package com.github.dropguard.summer.core;

import com.github.dropguard.summer.core.bean.BeanDeployment;
import com.github.dropguard.summer.core.bean.MockedBean;
import java.util.Map;

/**
 * SPI for building DI containers. Implementations are discovered via ServiceLoader and selected by
 * {@link #engine()}.
 */
@Internal
public interface ContainerEngine {

    Engine engine();

    BeanContainer build(
            BeanDeployment deployment,
            MockedBean[] mocks,
            Map<String, Object> overrides,
            String cacheKey,
            String className);
}
