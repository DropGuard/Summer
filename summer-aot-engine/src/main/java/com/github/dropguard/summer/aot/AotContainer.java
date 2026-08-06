package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.engine.BeanDeployment;
import com.github.dropguard.summer.engine.ContainerEngine;
import java.util.Map;

/**
 * AOT {@link ContainerEngine} implementation. The cache key and generated class name travel on the
 * deployment ({@link BeanDeployment#withCodegen(String, String)}) — they are AOT-specific and
 * therefore not part of the shared build signature.
 */
@Internal
public final class AotContainer implements ContainerEngine {

    @Override
    public Engine engine() {
        return Engine.AOT;
    }

    @Override
    public BeanContainer build(
            BeanDeployment deployment, MockedBean[] mocks, Map<String, Object> overrides) {
        if (deployment.cacheKey() == null || deployment.containerClassName() == null) {
            throw new IllegalStateException(
                    "AOT container build requires codegen parameters on the deployment ("
                            + "BeanDeployment.withCodegen(cacheKey, className))");
        }
        return AotEngine.buildAndCompile(
                deployment,
                deployment.cacheKey(),
                deployment.containerClassName(),
                mocks,
                overrides);
    }
}
