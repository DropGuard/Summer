package summer.grpc.server;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ErrorCode;
import summer.core.exception.SummerException;
import summer.grpc.exception.SummerGrpcException;

/**
 * Global gRPC exception interceptor that translates unhandled exceptions
 * into gRPC Status responses.
 *
 * <p>
 * Exception handler for gRPC services. Service implementations can
 * throw exceptions freely; this interceptor catches them at the boundary
 * and maps them to appropriate gRPC status codes.
 * </p>
 *
 * <pre>{@code
 * // In a service --no try-catch needed:
 * @Override
 * public void getUser(GetUserRequest req, StreamObserver<UserResponse> obs) {
 *     User user = userService.findById(req.getId());  // may throw
 *     obs.onNext(toResponse(user));
 *     obs.onCompleted();
 * }
 * }</pre>
 */
public class GrpcExceptionInterceptor implements ServerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(GrpcExceptionInterceptor.class);

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
			ServerCall<ReqT, RespT> call,
			Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {

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

		Status status = mapStatus(e);
		log.error("gRPC call failed: {} --{}", status.getCode(), e.getMessage(), e);

		Metadata trailers = new Metadata();
		call.close(status.withDescription(e.getMessage()), trailers);
	}

	/**
	 * Maps an exception to a gRPC {@link Status}.
	 */
	static Status mapStatus(Exception e) {
		if (e instanceof SummerGrpcException grpcEx) {
			return mapErrorCode(grpcEx.errorCode());
		}
		if (e instanceof SummerException summerEx) {
			return mapErrorCode(summerEx.errorCode());
		}
		if (e instanceof StatusRuntimeException sre) {
			return sre.getStatus();
		}
		if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
			return Status.INVALID_ARGUMENT;
		}
		return Status.INTERNAL;
	}

	private static Status mapErrorCode(ErrorCode code) {
		return switch (code) {
			case VALIDATION_FAILED, BODY_PARSE_ERROR -> Status.INVALID_ARGUMENT;
			case BEAN_NOT_FOUND, AMBIGUOUS_BEAN -> Status.UNAVAILABLE;
			case DATA_ACCESS_ERROR, DATA_SERIALIZATION_ERROR -> Status.INTERNAL;
			case GRPC_ERROR -> Status.INTERNAL;
			default -> Status.INTERNAL;
		};
	}
}

