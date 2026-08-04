package com.github.dropguard.summer.grpc.exception;

import io.grpc.Status;

/**
 * gRPC exception carrying an {@link Status}.
 *
 * <p>gRPC has its own error representation ({@code Status} = code + description), independent of
 * the framework's {@link com.github.dropguard.summer.core.ErrorCode} system. This exception bridges
 * the two: application code throws it with a gRPC status, and {@code GrpcExceptionInterceptor}
 * extracts the status directly — no mapping needed.
 *
 * <p>Does <em>not</em> extend {@link com.github.dropguard.summer.core.exception.SummerException}
 * because {@code ErrorCode} has no equivalent in the gRPC domain.
 */
public class SummerGrpcException extends RuntimeException {

    private final Status grpcStatus;

    public SummerGrpcException(Status status) {
        super(status.getDescription());
        this.grpcStatus = status;
    }

    public SummerGrpcException(Status status, Throwable cause) {
        super(status.getDescription(), cause);
        this.grpcStatus = status;
    }

    public Status getStatus() {
        return grpcStatus;
    }
}
