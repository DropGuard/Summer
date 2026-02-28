package summer.validation;

/**
 * Interface for validating request bodies.
 * Implementations should validate that a deserialized body object meets the required constraints.
 */
public interface BodyValidator {
    
    ValidationResult validate(Object body);
    
    boolean supports(Class<?> type);
}
