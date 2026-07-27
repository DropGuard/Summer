package com.github.dropguard.summer.grpc.exception;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.exception.SummerException;

/** Base class for all gRPC-related exceptions in Summer. */
public class SummerGrpcException extends SummerException {

    public SummerGrpcException(String message) {
        super(ErrorCode.GRPC_ERROR, message);
    }

    public SummerGrpcException(String message, Throwable cause) {
        super(ErrorCode.GRPC_ERROR, message, cause);
    }
}
