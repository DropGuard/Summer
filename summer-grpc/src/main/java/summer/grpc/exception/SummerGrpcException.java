package summer.grpc.exception;

import summer.core.ErrorCode;
import summer.core.SummerException;

/**
 * Base class for all gRPC-related exceptions in Summer.
 */
public class SummerGrpcException extends SummerException {
	public SummerGrpcException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public SummerGrpcException(ErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public SummerGrpcException(String message) {
		super(ErrorCode.GRPC_ERROR, message);
	}

	/**
	 * Backward-compatible constructor.
	 */
	public SummerGrpcException(String message, Throwable cause) {
		super(ErrorCode.GRPC_ERROR, message, cause);
	}
}
