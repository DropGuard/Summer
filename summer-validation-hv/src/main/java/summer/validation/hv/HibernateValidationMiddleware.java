package summer.validation.hv;

import summer.core.Component;
import summer.validation.BodyValidator;
import summer.validation.ValidationResult;
import summer.web.ValidationMiddleware;

import java.util.Optional;

/**
 * Hibernate Validator specific validation middleware.
 */
@Component
public class HibernateValidationMiddleware extends ValidationMiddleware {

    private final BodyValidator bodyValidator;

    public HibernateValidationMiddleware(HibernateBodyValidator bodyValidator) {
        super(bodyValidator);
        this.bodyValidator = bodyValidator;
    }
}
