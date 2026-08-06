package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/** Thrown when AOT code generation or compilation of generated sources fails. */
public class AotCompilationException extends SummerException {
    public AotCompilationException(String message, Throwable cause) {
        super(ErrorCode.CONFIG_AOT_COMPILATION_FAILED, message, cause);
    }
}
