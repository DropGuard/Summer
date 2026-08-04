package com.github.dropguard.summer.web;

import com.github.dropguard.summer.web.exception.BodyParseException;
import com.github.dropguard.summer.web.exception.ValidationException;
import io.avaje.validation.ConstraintViolationException;
import io.avaje.validation.Validator;
import java.io.IOException;
import java.util.List;

/**
 * Parses and validates HTTP request bodies.
 *
 * <p>Separates body parsing and validation concerns from {@link HttpContext}. The {@link
 * BodyConverter} handles serialization format (JSON, etc.) and the {@link Validator} handles
 * constraint validation.
 */
class BodyParser {

    private final BodyConverter converter;
    private final Validator validator;

    public BodyParser(BodyConverter converter, Validator validator) {
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
     * Parses and validates the request body.
     *
     * @param body raw request body bytes
     * @param contentType request content type
     * @param type target type
     * @return parsed and validated object
     * @throws BodyParseException if parsing fails
     * @throws ValidationException if validation fails
     */
    public <T> T parseAndValidate(byte[] body, String contentType, Class<T> type) {
        T parsed = parse(body, contentType, type);
        if (parsed != null) {
            try {
                validator.validate(parsed);
            } catch (ConstraintViolationException e) {
                List<String> errors = e.violations().stream().map(Object::toString).toList();
                throw new ValidationException(errors);
            }
        }
        return parsed;
    }
}
