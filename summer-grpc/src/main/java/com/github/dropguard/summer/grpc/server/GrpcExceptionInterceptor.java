package com.github.dropguard.summer.grpc.server;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.grpc.exception.SummerGrpcException;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global gRPC exception interceptor that translates unhandled exceptions into gRPC Status
 * responses.
 *
 * <p>Exception handler for gRPC services. Service implementations can throw exceptions freely; this
 * interceptor catches them at the boundary and maps them to appropriate gRPC status codes.
 *
 * <pre>{@code
 * // In a service --no try-catch needed:
 * @Override
 * public void getUser(GetUserRequest req, StreamObserver<UserResponse> obs) {
 * 	User user = userService.findById(req.getId()); // may throw
 * 	obs.onNext(toResponse(user));
 * 	obs.onCompleted();
 * }
 * }</pre>
 */
@Internal
public class GrpcExceptionInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Exception e) {
                    handleException(call, e);
                }
            }
        };
    }

    private <ReqT, RespT> void handleException(ServerCall<ReqT, RespT> call, Exception e) {
        // Unwrap reflection InvocationTargetException
        if (e.getCause() instanceof Exception cause
                && "java.lang.reflect.InvocationTargetException".equals(e.getClass().getName())) {
            e = cause;
        }

        Status status = toGrpcStatus(e);
        log.error("gRPC call failed: {} -- {}", status.getCode(), status.getDescription(), e);

        Metadata trailers = new Metadata();
        call.close(status, trailers);
    }

    /** Extracts the gRPC {@link Status} from an exception. */
    static Status toGrpcStatus(Exception e) {
        if (e instanceof SummerGrpcException grpcEx) {
            return grpcEx.getStatus();
        }
        if (e instanceof StatusRuntimeException sre) {
            return sre.getStatus();
        }
        return Status.INTERNAL.withDescription("Internal error");
    }
}
