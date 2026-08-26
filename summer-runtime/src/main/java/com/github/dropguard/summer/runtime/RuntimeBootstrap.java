package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.engine.BeanDeployment;
import java.util.List;
import java.util.Map;

/**
 * Production bootstrap entry for the runtime DI engine.
 *
 * <p>This is the runtime counterpart of the AOT engine's generated {@code
 * com.github.dropguard.summer.aot.generated.GeneratedAotContext}: both expose the static {@code
 * build(Object...)} contract that {@code com.github.dropguard.summer.engine.DiEngine} loads
 * reflectively at production startup. Separating the reflective entry point from the {@code
 * ContainerEngine} SPI implementation ( {@link RuntimeContainer}) keeps the two engine roles
 * symmetric with AOT ({@code GeneratedAotContext} vs {@code AotContainer}) and removes the overload
 * ambiguity of a single class carrying both.
 */
@Internal
public final class RuntimeBootstrap {

    private RuntimeBootstrap() {}

    /** Production entry point: builds the container from the application classpath index. */
    public static BeanContainer build(Object... externalBeans) {
        JandexIndexLoader.LoadedIndex prod = JandexIndexLoader.productionIndex();
        BeanDeployment deployment =
                BeanDeployment.forArchives(
                        prod.index(), prod.classToArchive(), prod.archiveIndexes());
        return RuntimeContainer.init(deployment, List.of(), Map.of(), externalBeans);
    }
}
