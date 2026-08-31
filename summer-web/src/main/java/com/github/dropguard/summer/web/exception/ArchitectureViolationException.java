package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when a request DTO class violates the Record requirement. */
public class ArchitectureViolationException extends HttpException {
    public ArchitectureViolationException(String className) {

        super(
                HttpStatus.BAD_REQUEST.code(),
                String.format(
                        "Architecture Violation: Class [%s] is not a Record. Summer enforces"
                            + " immutable Records for Request DTOs to ensure thread-safety,"
                            + " clarity, and future-proof performance. Please use 'record' instead"
                            + " of 'class' for your data transfer objects.",
                        className));
    }
}
