package summer.validation.hv;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import summer.core.Component;
import summer.validation.BodyValidator;
import summer.validation.ValidationResult;

/**
 * Jakarta Validation (Hibernate Validator) implementation of BodyValidator.
 */
@Component
public class HibernateBodyValidator implements BodyValidator, AutoCloseable {

	private final ValidatorFactory factory;
	private final Validator validator;

	public HibernateBodyValidator() {
		this.factory = Validation.buildDefaultValidatorFactory();
		this.validator = factory.getValidator();
	}

	@Override
	public void close() {
		factory.close();
	}

	@Override
	public ValidationResult validate(Object body) {
		if (body == null) {
			return ValidationResult.valid();
		}

		Set<ConstraintViolation<Object>> violations = validator.validate(body);

		if (violations.isEmpty()) {
			return ValidationResult.valid();
		}

		List<String> errors = violations.stream().map(v -> v.getPropertyPath().toString() + " " + v.getMessage())
				.collect(Collectors.toList());

		return ValidationResult.invalid(errors);
	}

	@Override
	public boolean supports(Class<?> type) {
		return true; // Support all types
	}
}
