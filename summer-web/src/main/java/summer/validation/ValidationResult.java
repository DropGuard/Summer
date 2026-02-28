package summer.validation;

import java.util.Collections;
import java.util.List;

/**
 * Represents the result of a validation operation.
 */
public class ValidationResult {
    
    private final boolean valid;
    private final List<String> errors;
    
    private ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors;
    }
    
    public static ValidationResult valid() {
        return new ValidationResult(true, Collections.emptyList());
    }
    
    public static ValidationResult invalid(List<String> errors) {
        return new ValidationResult(false, errors);
    }
    
    public static ValidationResult invalid(String error) {
        return new ValidationResult(false, Collections.singletonList(error));
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<String> getErrors() {
        return errors;
    }
}
