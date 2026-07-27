package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/** Thrown when a circular dependency is detected between beans. */
public class CircularDependencyException extends SummerException {

    public CircularDependencyException(String message) {
        super(ErrorCode.CIRCULAR_DEPENDENCY, message);
    }
}
