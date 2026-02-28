package summer.web;

import org.junit.jupiter.api.Test;
import summer.validation.ValidationResult;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationTest {

    @Test
    public void testNotNullValidation() {
        // Create a test object with null field1
        TestObject obj1 = new TestObject(null, "test", 10, 15, "test@example.com");
        Validator validator = new DefaultValidator();
        
        ValidationResult result1 = validator.validate(obj1);
        assertFalse(result1.isValid());
        assertTrue(result1.getErrors().size() >= 1);
        
        // Create a test object with valid field1
        TestObject obj2 = new TestObject("value", "test", 10, 15, "test@example.com");
        ValidationResult result2 = validator.validate(obj2);
        assertTrue(result2.isValid());
    }
    
    @Test
    public void testNotEmptyValidation() {
        // Create a test object with empty field2
        TestObject obj1 = new TestObject("value", "", 10, 15, "test@example.com");
        Validator validator = new DefaultValidator();
        
        ValidationResult result1 = validator.validate(obj1);
        assertFalse(result1.isValid());
        assertTrue(result1.getErrors().size() >= 1);
        
        // Create a test object with valid field2
        TestObject obj2 = new TestObject("value", "test", 10, 15, "test@example.com");
        ValidationResult result2 = validator.validate(obj2);
        assertTrue(result2.isValid());
    }
    
    @Test
    public void testMinValidation() {
        // Create test objects
        TestObject obj1 = new TestObject("value", "test", 5);
        TestObject obj2 = new TestObject("value", "test", 15);
        
        Validator validator = new DefaultValidator();
        
        // Validate obj1 should fail (value < min)
        ValidationResult result1 = validator.validate(obj1);
        assertFalse(result1.isValid());
        assertTrue(result1.getErrors().size() >= 1);
        
        // Validate obj2 should pass
        ValidationResult result2 = validator.validate(obj2);
        assertTrue(result2.isValid());
    }
    
    @Test
    public void testMaxValidation() {
        // Create test objects
        TestObject obj1 = new TestObject("value", "test", 15, 25);
        TestObject obj2 = new TestObject("value", "test", 15, 15);
        
        Validator validator = new DefaultValidator();
        
        // Validate obj1 should fail (value > max)
        ValidationResult result1 = validator.validate(obj1);
        assertFalse(result1.isValid());
        assertTrue(result1.getErrors().size() >= 1);
        
        // Validate obj2 should pass
        ValidationResult result2 = validator.validate(obj2);
        assertTrue(result2.isValid());
    }
    
    @Test
    public void testEmailValidation() {
        // Create test objects
        TestObject obj1 = new TestObject("value", "test", 15, 15, "invalid-email");
        TestObject obj2 = new TestObject("value", "test", 15, 15, "test@example.com");
        
        Validator validator = new DefaultValidator();
        
        // Validate obj1 should fail (invalid email)
        ValidationResult result1 = validator.validate(obj1);
        assertFalse(result1.isValid());
        assertTrue(result1.getErrors().size() >= 1);
        
        // Validate obj2 should pass
        ValidationResult result2 = validator.validate(obj2);
        assertTrue(result2.isValid());
    }
    
    // Test object with validation annotations
    public static class TestObject {
        
        @NotNull
        private String field1;
        
        @NotEmpty
        private String field2;
        
        @Min(10)
        private int field3;
        
        @Max(20)
        private int field4;
        
        @Email
        private String email;
        
        public TestObject() {
        }
        
        public TestObject(String field1, String field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
        
        public TestObject(String field1, String field2, int field3) {
            this.field1 = field1;
            this.field2 = field2;
            this.field3 = field3;
        }
        
        public TestObject(String field1, String field2, int field3, int field4) {
            this.field1 = field1;
            this.field2 = field2;
            this.field3 = field3;
            this.field4 = field4;
        }
        
        public TestObject(String field1, String field2, int field3, int field4, String email) {
            this.field1 = field1;
            this.field2 = field2;
            this.field3 = field3;
            this.field4 = field4;
            this.email = email;
        }
        
        // Getters and setters
        public String getField1() {
            return field1;
        }
        
        public void setField1(String field1) {
            this.field1 = field1;
        }
        
        public String getField2() {
            return field2;
        }
        
        public void setField2(String field2) {
            this.field2 = field2;
        }
        
        public int getField3() {
            return field3;
        }
        
        public void setField3(int field3) {
            this.field3 = field3;
        }
        
        public int getField4() {
            return field4;
        }
        
        public void setField4(int field4) {
            this.field4 = field4;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
    }
}
