package com.github.dropguard.summer.engine;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.bean.MockedBean;
import java.util.Map;

/**
 * SPI for building DI containers. Implementations are discovered via ServiceLoader and selected by
 * {@link #engine()}.
 *
 * <p>The build signature carries only engine-agnostic inputs — deployment, mocks, and config
 * overrides. AOT-specific codegen parameters (cache key, generated class name) travel on the
 * deployment itself ({@link BeanDeployment#withCodegen(String, String)}); the runtime engine
 * ignores them, so they never surface in this shared contract.
 */
public interface ContainerEngine {

    Engine engine();

    BeanContainer build(
            BeanDeployment deployment, MockedBean[] mocks, Map<String, Object> overrides);
}
