package summer.core.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.ErrorCode;

/**
 * Tests for Summer exception hierarchy.
 */
class ExceptionTest {

	@Test
	void shouldCreateBeansExceptionWithErrorCode() {
		BeansException ex = new BeansException(ErrorCode.BEAN_CREATION_FAILED, "Test message");
		assertEquals("Test message", ex.getMessage());
	}

	@Test
	void shouldCreateBeansExceptionWithErrorCodeAndCause() {
		Exception cause = new RuntimeException("Root cause");
		BeansException ex = new BeansException(ErrorCode.BEAN_CREATION_FAILED, "Test message", cause);
		assertEquals("Test message", ex.getMessage());
		assertSame(cause, ex.getCause());
	}

	@Test
	void shouldCreateBeansExceptionWithMessage() {
		BeansException ex = new BeansException(ErrorCode.INTERNAL_ERROR, "Test message");
		assertEquals("Test message", ex.getMessage());
	}

	@Test
	void shouldCreateNoSuchBeanException() {
		NoSuchBeanException ex = new NoSuchBeanException("No bean found");
		assertEquals("No bean found", ex.getMessage());
		assertInstanceOf(BeansException.class, ex);
	}

	@Test
	void shouldCreateCircularDependencyException() {
		CircularDependencyException ex = new CircularDependencyException("Circular dependency");
		assertEquals("Circular dependency", ex.getMessage());
		assertInstanceOf(BeansException.class, ex);
	}

	@Test
	void shouldCreateBeanCreationException() {
		Exception cause = new RuntimeException("Root cause");
		BeanCreationException ex = new BeanCreationException("Creation failed", cause);
		assertEquals("Creation failed", ex.getMessage());
		assertSame(cause, ex.getCause());
	}

	@Test
	void shouldCreateAmbiguousBeanException() {
		AmbiguousBeanException ex = new AmbiguousBeanException("Ambiguous bean");
		assertEquals("Ambiguous bean", ex.getMessage());
		assertInstanceOf(BeansException.class, ex);
	}

	@Test
	void shouldCreateConfigurationExceptionWithErrorCode() {
		ConfigurationException ex = new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR, "Config error");
		assertEquals("Config error", ex.getMessage());
	}

	@Test
	void shouldCreateConfigurationExceptionWithErrorCodeAndCause() {
		Exception cause = new RuntimeException("Parse error");
		ConfigurationException ex = new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR, "Config error", cause);
		assertEquals("Config error", ex.getMessage());
		assertSame(cause, ex.getCause());
	}

	@Test
	void shouldCreateConfigurationExceptionWithMessage() {
		ConfigurationException ex = new ConfigurationException("Config error");
		assertEquals("Config error", ex.getMessage());
	}

	@Test
	void shouldCreateConfigurationExceptionWithMessageAndCause() {
		Exception cause = new RuntimeException("Parse error");
		ConfigurationException ex = new ConfigurationException("Config error", cause);
		assertEquals("Config error", ex.getMessage());
		assertSame(cause, ex.getCause());
	}

	@Test
	void shouldCreateDataAccessException() {
		DataAccessException ex = new DataAccessException("Data access failed");
		assertEquals("Data access failed", ex.getMessage());
	}

	@Test
	void shouldCreateDataAccessExceptionWithCause() {
		Exception cause = new RuntimeException("DB error");
		DataAccessException ex = new DataAccessException("Data access failed", cause);
		assertEquals("Data access failed", ex.getMessage());
		assertSame(cause, ex.getCause());
	}

	@Test
	void shouldCreateDataSerializationException() {
		DataSerializationException ex = new DataSerializationException("Serialization failed");
		assertEquals("Serialization failed", ex.getMessage());
	}

	@Test
	void shouldCreateDataSerializationExceptionWithCause() {
		Exception cause = new RuntimeException("Parse error");
		DataSerializationException ex = new DataSerializationException("Serialization failed", cause);
		assertEquals("Serialization failed", ex.getMessage());
		assertSame(cause, ex.getCause());
	}

	@Test
	void shouldCreateDuplicateReplacementException() {
		DuplicateReplacementException ex = new DuplicateReplacementException(String.class, Integer.class, Number.class);
		String message = ex.getMessage();
		assertTrue(message.contains("String"));
		assertTrue(message.contains("Integer"));
		assertTrue(message.contains("Number"));
	}

	@Test
	void shouldCreateAotContextNotFoundException() {
		AotContextNotFoundException ex = new AotContextNotFoundException();
		assertNotNull(ex.getMessage());
		assertTrue(ex.getMessage().contains("AOT Context not found"));
	}
}