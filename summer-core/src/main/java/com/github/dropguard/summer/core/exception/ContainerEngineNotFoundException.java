package com.github.dropguard.summer.core.exception;

/**
 * Thrown when no {@code com.github.dropguard.summer.engine.ContainerEngine} implementation is found
 * for the requested {@link com.github.dropguard.summer.core.Engine}.
 *
 * <p>Typically means the corresponding module ({@code summer-runtime} or {@code summer-aot-engine})
 * is missing from the classpath.
 */
public class ContainerEngineNotFoundException extends RuntimeException {

    public ContainerEngineNotFoundException(String message) {
        super(message);
    }
}
