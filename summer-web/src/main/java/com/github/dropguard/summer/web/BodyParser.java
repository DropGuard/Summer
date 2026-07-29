mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.exception.BodyParseException;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.exception.ValidationException;
mport com.github.dropguard.summer.core.Internal;
import io.avaje.validation.ConstraintViolationException;
mport com.github.dropguard.summer.core.Internal;
import io.avaje.validation.Validator;
mport com.github.dropguard.summer.core.Internal;
import java.io.IOException;
mport com.github.dropguard.summer.core.Internal;
import java.util.List;
@Internal
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Parses and validates HTTP request bodies.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Separates body parsing and validation concerns from {@link HttpContext}. The {@link
mport com.github.dropguard.summer.core.Internal;
 * BodyConverter} handles serialization format (JSON, etc.) and the {@link Validator} handles
mport com.github.dropguard.summer.core.Internal;
 * constraint validation.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class BodyParser {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final BodyConverter converter;
mport com.github.dropguard.summer.core.Internal;
    private final Validator validator;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public BodyParser(BodyConverter converter, Validator validator) {
mport com.github.dropguard.summer.core.Internal;
        this.converter = converter;
mport com.github.dropguard.summer.core.Internal;
        this.validator = validator;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Returns the body converter used for serialization. */
mport com.github.dropguard.summer.core.Internal;
    public BodyConverter converter() {
mport com.github.dropguard.summer.core.Internal;
        return converter;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Parses the request body to the specified type.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param body raw request body bytes
mport com.github.dropguard.summer.core.Internal;
     * @param contentType request content type
mport com.github.dropguard.summer.core.Internal;
     * @param type target type
mport com.github.dropguard.summer.core.Internal;
     * @return parsed object
mport com.github.dropguard.summer.core.Internal;
     * @throws BodyParseException if parsing fails
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public <T> T parse(byte[] body, String contentType, Class<T> type) {
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            return converter.read(body, type);
mport com.github.dropguard.summer.core.Internal;
        } catch (IOException e) {
mport com.github.dropguard.summer.core.Internal;
            throw new BodyParseException(contentType, e);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Parses and validates the request body.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param body raw request body bytes
mport com.github.dropguard.summer.core.Internal;
     * @param contentType request content type
mport com.github.dropguard.summer.core.Internal;
     * @param type target type
mport com.github.dropguard.summer.core.Internal;
     * @return parsed and validated object
mport com.github.dropguard.summer.core.Internal;
     * @throws BodyParseException if parsing fails
mport com.github.dropguard.summer.core.Internal;
     * @throws ValidationException if validation fails
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public <T> T parseAndValidate(byte[] body, String contentType, Class<T> type) {
mport com.github.dropguard.summer.core.Internal;
        T parsed = parse(body, contentType, type);
mport com.github.dropguard.summer.core.Internal;
        if (parsed != null) {
mport com.github.dropguard.summer.core.Internal;
            try {
mport com.github.dropguard.summer.core.Internal;
                validator.validate(parsed);
mport com.github.dropguard.summer.core.Internal;
            } catch (ConstraintViolationException e) {
mport com.github.dropguard.summer.core.Internal;
                List<String> errors = e.violations().stream().map(Object::toString).toList();
mport com.github.dropguard.summer.core.Internal;
                throw new ValidationException(errors);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return parsed;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
