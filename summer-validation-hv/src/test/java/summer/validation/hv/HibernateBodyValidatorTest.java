package summer.validation.hv;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.junit.jupiter.api.Test;
import summer.validation.BodyValidator;
import summer.validation.ValidationResult;

import static org.junit.jupiter.api.Assertions.*;

public class HibernateBodyValidatorTest {
    
    @Test
    public void testValidObjectValidation() {
        // Create validator instance
        BodyValidator validator = new HibernateBodyValidator();
        
        // Create valid object
        UserRequest validUser = new UserRequest();
        validUser.setName("John Doe");
        validUser.setEmail("john@example.com");
        validUser.setAge(25);
        
        // Validate
        ValidationResult result = validator.validate(validUser);
        
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }
    
    @Test
    public void testInvalidObjectValidation() {
        // Create validator instance
        BodyValidator validator = new HibernateBodyValidator();
        
        // Create invalid object
        UserRequest invalidUser = new UserRequest();
        invalidUser.setName(""); // Empty name
        invalidUser.setEmail("invalid-email"); // Invalid email
        invalidUser.setAge(17); // Age under 18
        
        // Validate
        ValidationResult result = validator.validate(invalidUser);
        
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().size() >= 3);
        
        // Check for specific error messages
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("name")));
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("email")));
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("age")));
    }
    
    @Test
    public void testNullObjectValidation() {
        // Create validator instance
        BodyValidator validator = new HibernateBodyValidator();
        
        // Validate null object
        ValidationResult result = validator.validate(null);
        
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }
    
    // Test object with validation annotations
    public static class UserRequest {
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.NotEmpty
        private String name;
        
        @jakarta.validation.constraints.Email
        private String email;
        
        @jakarta.validation.constraints.Min(value = 18, message = "Age must be at least 18")
        private int age;
        
        // Getters and setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public int getAge() {
            return age;
        }
        
        public void setAge(int age) {
            this.age = age;
        }
        }
}
