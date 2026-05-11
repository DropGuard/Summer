package summer.web;

import java.util.List;
import summer.validation.BodyValidator;
import summer.validation.ValidationResult;

/**
 * Encapsulates the HTTP request and response for a single web exchange.
 */
public class WebContext {
    private final Request request;
    private final Response response;
    private final BodyValidator validator;
    private final List<BodyConverter> converters;

    public WebContext(Request request, Response response) {
        this(request, response, null, List.of());
    }

    public WebContext(Request request, Response response, BodyValidator validator, List<BodyConverter> converters) {
        this.request = request;
        this.response = response;
        this.validator = validator;
        this.converters = converters;
    }

    public Request request() {
        return request;
    }

    public Response response() {
        return response;
    }

    /**
     * Parses the body using a matching converter and performs validation if a validator is present.
     */
    public <T> T body(Class<T> type) {
        String contentType = request.getContentType();
        BodyConverter converter = findConverter(contentType);
        
        try {
            T body = converter.read(request.getBody(), type);
            if (body != null && validator != null && validator.supports(type)) {
                ValidationResult result = validator.validate(body);
                if (!result.isValid()) {
                    throw new RuntimeException("Validation failed: " + String.join(", ", result.getErrors()));
                }
            }
            return body;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to parse body with " + converter.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public BodyConverter findConverter(String contentType) {
        if (converters != null) {
            for (BodyConverter converter : converters) {
                if (converter.supports(contentType)) {
                    return converter;
                }
            }
        }
        // Fallback to JSON if no match found (maintain default behavior)
        return new JsonBodyConverter();
    }

    public List<BodyConverter> converters() {
        return converters;
    }

    // Shortcut methods for convenience
    public String path() {
        return request.getPath();
    }

    public String method() {
        return request.getMethod();
    }

    public void ok(Object result) {
        String acceptHeader = request.getHeader("Accept");
        BodyConverter converter = findConverter(acceptHeader);
        response.ok(result, converter);
    }

    public void notFound() {
        response.notFound();
    }

    public void error(Exception e) {
        response.error(e);
    }
}
