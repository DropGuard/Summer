package com.github.dropguard.summer.core.exception;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.ErrorCode;
import org.junit.jupiter.api.Test;

/** Tests for Summer exception hierarchy. */
class ExceptionTest {

    @Test
    void shouldCreateNoSuchBeanException() {
        NoSuchBeanException ex = new NoSuchBeanException("No bean found");
        assertEquals("No bean found", ex.getMessage());
        assertInstanceOf(SummerException.class, ex);
    }

    @Test
    void shouldCreateCircularDependencyException() {
        CircularDependencyException ex = new CircularDependencyException("Circular dependency");
        assertEquals("Circular dependency", ex.getMessage());
        assertInstanceOf(SummerException.class, ex);
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
        assertInstanceOf(SummerException.class, ex);
    }

    @Test
    void shouldCreateConfigurationExceptionWithErrorCode() {
        ConfigurationException ex =
                new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR, "Config error");
        assertEquals("Config error", ex.getMessage());
    }

    @Test
    void shouldCreateConfigurationExceptionWithErrorCodeAndCause() {
        Exception cause = new RuntimeException("Parse error");
        ConfigurationException ex =
                new ConfigurationException(ErrorCode.CONFIG_PARSE_ERROR, "Config error", cause);
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
        DataSerializationException ex =
                new DataSerializationException("Serialization failed", cause);
        assertEquals("Serialization failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
