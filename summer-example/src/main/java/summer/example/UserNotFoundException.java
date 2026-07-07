package summer.example;

import summer.core.ErrorCode;
import summer.core.exception.SummerException;

public class UserNotFoundException extends SummerException {
	public UserNotFoundException(String message) {
		super(ErrorCode.INTERNAL_ERROR, message);
	}

	public UserNotFoundException(String message, Throwable cause) {
		super(ErrorCode.INTERNAL_ERROR, message, cause);
	}
}
