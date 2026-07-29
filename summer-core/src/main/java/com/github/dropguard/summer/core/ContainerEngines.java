package com.github.dropguard.summer.core;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves {@link ContainerEngine} implementations via ServiceLoader. */
@Internal
public final class ContainerEngines {

    private static final Map<Engine, ContainerEngine> ENGINES = new ConcurrentHashMap<>();

    private ContainerEngines() {}

    public static ContainerEngine forEngine(Engine engine) {
        return ENGINES.computeIfAbsent(
                engine,
                e ->
                        ServiceLoader.load(ContainerEngine.class).stream()
                                .map(ServiceLoader.Provider::get)
                                .filter(ce -> ce.engine() == e)
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "No ContainerEngine found for "
                                                                + e
                                                                + ". Ensure the engine module is on"
                                                                + " the classpath.")));
    }
}
