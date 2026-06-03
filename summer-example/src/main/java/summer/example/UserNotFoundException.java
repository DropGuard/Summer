package summer.example;

import summer.core.exception.SummerException;

public class UserNotFoundException extends SummerException {
	public UserNotFoundException(String message) {
		super(message);
	}

	public UserNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}
}
