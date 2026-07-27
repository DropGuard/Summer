package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/** Thrown when AOT context is requested but not found on the classpath. */
public class AotContextNotFoundException extends SummerException {
    public AotContextNotFoundException() {
        super(
                ErrorCode.CONFIG_AOT_CONTEXT_NOT_FOUND,
                "AOT Context not found. Ensure summer-maven-plugin is configured and ran during"
                        + " build.");
    }
}
