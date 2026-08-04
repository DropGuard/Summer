package com.github.dropguard.summer.core;

/**
 * Structured error codes for the Summer framework. Each code uniquely identifies an error
 * condition, enabling programmatic error handling, i18n, and client-side error mapping.
 */
public enum ErrorCode {
    // Beans (1xxx)
    BEAN_NOT_FOUND(1001, "No bean found for the requested type"),
    AMBIGUOUS_BEAN(1002, "Multiple beans match the requested type"),
    CIRCULAR_DEPENDENCY(1003, "Circular dependency detected between beans"),
    BEAN_CREATION_FAILED(1004, "Failed to create bean instance"),
    UNSUPPORTED_INJECTION(1005, "Unsupported injection type"),

    // Configuration (2xxx)
    CONFIG_MISSING_INDEX(2001, "Jandex index not found on classpath"),
    CONFIG_PARSE_ERROR(2002, "Failed to parse configuration"),
    CONFIG_MISSING_FIELD(2003, "Required configuration field is missing"),
    CONFIG_VALIDATION_FAILED(2004, "Configuration validation failed"),
    CONFIG_AOT_CONTEXT_NOT_FOUND(2005, "AOT context not generated"),
    CONFIG_RUNTIME_NOT_ON_CLASSPATH(2006, "Runtime engine not on classpath"),

    // Web (3xxx)
    VALIDATION_FAILED(3001, "Request validation failed"),
    BODY_PARSE_ERROR(3002, "Failed to parse request body"),
    ROUTE_CONFLICT(3003, "Route registration conflict"),
    ARCHITECTURE_VIOLATION(3004, "Architecture constraint violated"),

    // Data (4xxx)
    DATA_ACCESS_ERROR(4001, "Database access error"),
    DATA_SERIALIZATION_ERROR(4002, "Data serialization/deserialization error"),

    // AOP (5xxx)
    AOP_ERROR(5001, "AOP proxy creation failed"),
    AOP_NO_INTERFACE(
            5002, "AOP proxy requires the target bean to implement at least one interface"),

    // Transaction (6xxx)
    TRANSACTION_ERROR(6001, "Transaction operation failed"),

    // Internal (9xxx)
    INTERNAL_ERROR(9999, "Internal framework error");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
