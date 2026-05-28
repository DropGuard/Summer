package summer.web.exception;

import java.util.List;
import summer.core.ErrorCode;

/**
 * Thrown when request body validation fails.
 */
public class ValidationException extends SummerWebException {
	private final List<String> errors;

	public ValidationException(List<String> errors) {
		super(ErrorCode.VALIDATION_FAILED, 400, "Validation failed: " + String.join(", ", errors));
		this.errors = errors;
	}

	public List<String> errors() {
		return errors;
	}
}
