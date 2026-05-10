package summer.validation.hv;

import summer.core.Component;
import summer.web.middleware.ValidationMiddleware;

/**
 * Hibernate Validator specific validation middleware.
 */
@Component
public class HibernateValidationMiddleware extends ValidationMiddleware {

	public HibernateValidationMiddleware(HibernateBodyValidator bodyValidator) {
		super(bodyValidator);
	}
}
