package com.github.dropguard.summer.core;

/**
 * Closed vocabulary of framework-assembly failure identifiers, one per {@code SummerException}
 * subtype. Segments: 1xxx bean/DI, 2xxx configuration, 3xxx web, 4xxx data, 5xxx AOP, 6xxx
 * transaction, 9xxx internal. Consumed programmatically (engine branching, assertions) via {@code
 * SummerException.errorCode()} — the HTTP-side {@code HttpException} family deliberately lives
 * outside this enum: an HTTP response already carries its own identifier, the status code.
 */
public enum ErrorCode {
    // Beans (1xxx)
    BEAN_NOT_FOUND, // No bean found for the requested type
    AMBIGUOUS_BEAN, // Multiple beans match the requested type
    CIRCULAR_DEPENDENCY, // Circular dependency detected between beans
    BEAN_CREATION_FAILED, // Failed to create bean instance
    UNSUPPORTED_INJECTION, // Unsupported injection type

    // Configuration (2xxx)
    CONFIG_MISSING_INDEX, // Jandex index not found on classpath
    CONFIG_PARSE_ERROR, // Failed to parse configuration
    CONFIG_MISSING_FIELD, // Required configuration field is missing
    CONFIG_VALIDATION_FAILED, // Configuration validation failed
    CONFIG_AOT_CONTEXT_NOT_FOUND, // AOT context not generated
    CONFIG_RUNTIME_NOT_ON_CLASSPATH, // Runtime engine not on classpath
    CONFIG_AOT_COMPILATION_FAILED, // AOT code generation or compilation failed
    CONFIG_ENGINE_NOT_FOUND, // No ContainerEngine found for the requested engine

    // Web (3xxx)
    ROUTE_CONFLICT, // Route registration conflict
    ROUTE_MATCH_ERROR, // Request path could not be matched
    HANDLER_INVOCATION_FAILED, // Failed to invoke request handler
    WEB_HANDLER_CONFLICT, // Two @ExceptionHandler methods declare the same exception type

    // Data (4xxx)
    DATA_ACCESS_ERROR, // Database access error
    DATA_SERIALIZATION_ERROR, // Data serialization/deserialization error

    // AOP (5xxx)
    AOP_ERROR, // AOP proxy creation failed
    AOP_NO_INTERFACE, // AOP proxy requires the target bean to implement at least one interface

    // Transaction (6xxx)
    TRANSACTION_ERROR, // Transaction operation failed

    // Internal (9xxx)
    INTERNAL_ERROR // Internal framework error
}
