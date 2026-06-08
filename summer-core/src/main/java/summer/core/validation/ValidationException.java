package summer.core.validation;

import summer.core.ErrorCode;
import summer.core.exception.ConfigurationException;

/**
 * Thrown when a bean fails validation after property binding.
 */
public class ValidationException extends ConfigurationException {

	public ValidationException(String message) {
		super(ErrorCode.CONFIG_VALIDATION_FAILED, message);
	}
}
