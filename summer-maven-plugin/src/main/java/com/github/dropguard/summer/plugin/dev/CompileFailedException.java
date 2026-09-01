package com.github.dropguard.summer.plugin.dev;

/**
 * Thrown by {@link DevEnvironment#rebuild} when {@code HotCompiler.compile} returns false. Carries
 * no diagnostics: {@code HotCompiler} already logs each javac diagnostic at ERROR, so the message
 * is only the reason for the throw; the actual compile output lives in the log.
 */
public final class CompileFailedException extends RuntimeException {
    public CompileFailedException(String message) {
        super(message);
    }
}
