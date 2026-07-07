package summer.grpc.exception;

import summer.core.ErrorCode;
import summer.core.exception.SummerException;

/**
 * Base class for all gRPC-related exceptions in Summer.
 */
public class SummerGrpcException extends SummerException {

	public SummerGrpcException(String message) {
		super(ErrorCode.GRPC_ERROR, message);
	}

	public SummerGrpcException(String message, Throwable cause) {
		super(ErrorCode.GRPC_ERROR, message, cause);
	}
}
