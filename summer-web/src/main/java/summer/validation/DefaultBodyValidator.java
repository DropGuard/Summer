package summer.validation;

import summer.core.Component;
import summer.web.DefaultValidator;

/**
 * Default BodyValidator implementation that adapts to the built-in DefaultValidator.
 * This provides basic validation support without requiring external validation libraries.
 */
@Component
public class DefaultBodyValidator implements BodyValidator {
    
    private final DefaultValidator defaultValidator;
    
    public DefaultBodyValidator() {
        this.defaultValidator = new DefaultValidator();
    }
    
    @Override
    public ValidationResult validate(Object body) {
        if (body == null) {
            return ValidationResult.valid();
        }
        
        return defaultValidator.validate(body);
    }
    
    @Override
    public boolean supports(Class<?> type) {
        return true; // Supports all types
    }
}