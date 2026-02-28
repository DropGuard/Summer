package summer.validation.hv;

import summer.core.Component;
import summer.validation.BodyValidator;
import summer.validation.ValidationResult;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Jakarta Validation (Hibernate Validator) implementation of BodyValidator.
 */
@Component
public class HibernateBodyValidator implements BodyValidator {

    private final Validator validator;

    public HibernateBodyValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
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

        List<String> errors = violations.stream()
                .map(v -> v.getPropertyPath().toString() + " " + v.getMessage())
                .collect(Collectors.toList());

        return ValidationResult.invalid(errors);
    }

    @Override
    public boolean supports(Class<?> type) {
        return true; // Support all types
    }
}
