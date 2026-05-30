package summer.web.exception;

import summer.core.ErrorCode;
import summer.web.HttpStatus;

/**
 * Thrown when @Valid is used but no BodyValidator component is found.
 */
public class ValidatorNotFoundException extends SummerWebException {
	public ValidatorNotFoundException() {
		super(ErrorCode.VALIDATION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR,
				"Validation is required for parameter annotated with @Valid, but no BodyValidator component was found. Did you forget to import a validation module (e.g. summer-validation-hv)?");
	}
}
