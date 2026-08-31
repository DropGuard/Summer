package com.github.dropguard.summer.web.exception;

import com.github.dropguard.summer.web.HttpStatus;

/** Thrown when a business conflict is detected (e.g., duplicate resource). */
public class ConflictException extends HttpException {
    public ConflictException(String message) {

        super(HttpStatus.CONFLICT.code(), message);
    }

    /** Convenience method */
    public static ConflictException conflict(String message) {
        return new ConflictException(message);
    }
}
