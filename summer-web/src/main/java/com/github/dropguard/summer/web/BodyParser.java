package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.validation.Result;
import com.github.dropguard.summer.core.validation.Validator;
import com.github.dropguard.summer.web.exception.BodyParseException;
import com.github.dropguard.summer.web.exception.ValidationException;
import java.io.IOException;

/**
 * Parses and validates HTTP request bodies.
 *
 * <p>Separates body parsing and validation concerns from {@link HttpContext}. The {@link
 * BodyConverter} handles serialization format (JSON, etc.) and the {@link Validator} handles
 * constraint validation using the framework's result-acumulation model.
 */
class BodyParser {

    private final BodyConverter converter;
    private final Validator<?> validator;

    public BodyParser(BodyConverter converter, Validator<?> validator) {
        this.converter = converter;
        this.validator = validator;
    }

    /** Returns the body converter used for serialization. */
    public BodyConverter converter() {
        return converter;
    }

    /**
     * Parses the request body to the specified type.
     *
     * @param body raw request body bytes
     * @param contentType request content type
     * @param type target type
     * @return parsed object
     * @throws BodyParseException if parsing fails
     */
    public <T> T parse(byte[] body, String contentType, Class<T> type) {
        try {
            return converter.read(body, type);
        } catch (IOException e) {
            throw new BodyParseException(contentType, e);
        }
    }

    /**
     * Parses and validates the request body using the framework's result-acumulation model. All
     * violations are collected and reported together via {@link ValidationException}.
     *
     * @param body raw request body bytes
     * @param contentType request content type
     * @param type target type
     * @return parsed object
     * @throws BodyParseException if parsing fails
     * @throws ValidationException if validation fails (with every accumulated violation)
     */
    public <T> T parseAndValidate(byte[] body, String contentType, Class<T> type) {
        T parsed = parse(body, contentType, type);
        if (parsed != null) {
            Result result = new Result();
            @SuppressWarnings({"unchecked", "rawtypes"})
            Validator<Object> typed = (Validator) validator;
            typed.validate(parsed, result);
            result.throwIfInvalid();
        }
        return parsed;
    }
}
