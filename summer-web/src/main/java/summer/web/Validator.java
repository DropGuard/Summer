package summer.web;

import summer.validation.ValidationResult;

/**
 * Interface for validating objects.
 * Implementations should provide specific validation logic.
 */
public interface Validator {
    
    /**
     * Validates the given object.
     * 
     * @param object The object to validate
     * @return A ValidationResult indicating if the object is valid
     */
    ValidationResult validate(Object object);
    
    /**
     * Returns the class type this validator supports.
     * 
     * @return The class type this validator supports
     */
    Class<?> supports();
}
