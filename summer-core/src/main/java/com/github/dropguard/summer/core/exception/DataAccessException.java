package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/** Thrown when a database access operation fails. */
public class DataAccessException extends SummerException {
    public DataAccessException(String message) {
        super(ErrorCode.DATA_ACCESS_ERROR, message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(ErrorCode.DATA_ACCESS_ERROR, message, cause);
    }
}
