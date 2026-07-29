mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.grpc.server;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.ErrorCode;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.SummerException;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.grpc.exception.SummerGrpcException;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.ForwardingServerCallListener;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.Metadata;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.ServerCall;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.ServerCallHandler;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.ServerInterceptor;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.Status;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.StatusRuntimeException;
mport com.github.dropguard.summer.core.Internal;
import org.slf4j.Logger;
mport com.github.dropguard.summer.core.Internal;
import org.slf4j.LoggerFactory;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
@Internal
mport com.github.dropguard.summer.core.Internal;
 * Global gRPC exception interceptor that translates unhandled exceptions into gRPC Status
mport com.github.dropguard.summer.core.Internal;
 * responses.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Exception handler for gRPC services. Service implementations can throw exceptions freely; this
mport com.github.dropguard.summer.core.Internal;
 * interceptor catches them at the boundary and maps them to appropriate gRPC status codes.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <pre>{@code
mport com.github.dropguard.summer.core.Internal;
 * // In a service --no try-catch needed:
mport com.github.dropguard.summer.core.Internal;
 * @Override
mport com.github.dropguard.summer.core.Internal;
 * public void getUser(GetUserRequest req, StreamObserver<UserResponse> obs) {
mport com.github.dropguard.summer.core.Internal;
 * 	User user = userService.findById(req.getId()); // may throw
mport com.github.dropguard.summer.core.Internal;
 * 	obs.onNext(toResponse(user));
mport com.github.dropguard.summer.core.Internal;
 * 	obs.onCompleted();
mport com.github.dropguard.summer.core.Internal;
 * }
mport com.github.dropguard.summer.core.Internal;
 * }</pre>
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class GrpcExceptionInterceptor implements ServerInterceptor {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionInterceptor.class);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
mport com.github.dropguard.summer.core.Internal;
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
mport com.github.dropguard.summer.core.Internal;
            @Override
mport com.github.dropguard.summer.core.Internal;
            public void onHalfClose() {
mport com.github.dropguard.summer.core.Internal;
                try {
mport com.github.dropguard.summer.core.Internal;
                    super.onHalfClose();
mport com.github.dropguard.summer.core.Internal;
                } catch (Exception e) {
mport com.github.dropguard.summer.core.Internal;
                    handleException(call, e);
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        };
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private <ReqT, RespT> void handleException(ServerCall<ReqT, RespT> call, Exception e) {
mport com.github.dropguard.summer.core.Internal;
        // Unwrap reflection InvocationTargetException
mport com.github.dropguard.summer.core.Internal;
        if (e.getCause() instanceof Exception cause
mport com.github.dropguard.summer.core.Internal;
                && "java.lang.reflect.InvocationTargetException".equals(e.getClass().getName())) {
mport com.github.dropguard.summer.core.Internal;
            e = cause;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        Status status = mapStatus(e);
mport com.github.dropguard.summer.core.Internal;
        log.error("gRPC call failed: {} --{}", status.getCode(), e.getMessage(), e);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        Metadata trailers = new Metadata();
mport com.github.dropguard.summer.core.Internal;
        call.close(status.withDescription(e.getMessage()), trailers);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Maps an exception to a gRPC {@link Status}. */
mport com.github.dropguard.summer.core.Internal;
    static Status mapStatus(Exception e) {
mport com.github.dropguard.summer.core.Internal;
        if (e instanceof SummerGrpcException grpcEx) {
mport com.github.dropguard.summer.core.Internal;
            return mapErrorCode(grpcEx.errorCode());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (e instanceof SummerException summerEx) {
mport com.github.dropguard.summer.core.Internal;
            return mapErrorCode(summerEx.errorCode());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (e instanceof StatusRuntimeException sre) {
mport com.github.dropguard.summer.core.Internal;
            return sre.getStatus();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
mport com.github.dropguard.summer.core.Internal;
            return Status.INVALID_ARGUMENT;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return Status.INTERNAL;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static Status mapErrorCode(ErrorCode code) {
mport com.github.dropguard.summer.core.Internal;
        return switch (code) {
mport com.github.dropguard.summer.core.Internal;
            case VALIDATION_FAILED, BODY_PARSE_ERROR -> Status.INVALID_ARGUMENT;
mport com.github.dropguard.summer.core.Internal;
            case BEAN_NOT_FOUND, AMBIGUOUS_BEAN -> Status.UNAVAILABLE;
mport com.github.dropguard.summer.core.Internal;
            case DATA_ACCESS_ERROR, DATA_SERIALIZATION_ERROR -> Status.INTERNAL;
mport com.github.dropguard.summer.core.Internal;
            case GRPC_ERROR -> Status.INTERNAL;
mport com.github.dropguard.summer.core.Internal;
            default -> Status.INTERNAL;
mport com.github.dropguard.summer.core.Internal;
        };
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
