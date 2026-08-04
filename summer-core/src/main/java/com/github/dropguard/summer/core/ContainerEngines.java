package com.github.dropguard.summer.core;

import java.util.ServiceLoader;

/** Resolves {@link ContainerEngine} implementations via ServiceLoader. */
@Internal
public final class ContainerEngines {

    private ContainerEngines() {}

    public static ContainerEngine forEngine(Engine engine) {
        return ServiceLoader.load(ContainerEngine.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(ce -> ce.engine() == engine)
                .findFirst()
                .orElseThrow(
                        () ->
                                new com.github.dropguard.summer.core.exception
                                        .ContainerEngineNotFoundException(
                                        "No ContainerEngine found for "
                                                + engine
                                                + ". Ensure the engine module (summer-runtime or"
                                                + " summer-aot-engine) is on the classpath."));
    }
}
