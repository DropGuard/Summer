package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;

/** Thrown when data serialization or deserialization fails. */
public class DataSerializationException extends SummerException {
    public DataSerializationException(String message) {
        super(ErrorCode.DATA_SERIALIZATION_ERROR, message);
    }

    public DataSerializationException(String message, Throwable cause) {
        super(ErrorCode.DATA_SERIALIZATION_ERROR, message, cause);
    }
}
