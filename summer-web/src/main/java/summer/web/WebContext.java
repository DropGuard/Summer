package summer.web;

import summer.validation.BodyValidator;
import summer.validation.ValidationResult;

/**
 * Encapsulates the HTTP request and response for a single web exchange.
 */
public class WebContext {
    private final Request request;
    private final Response response;
    private final BodyValidator validator;

    public WebContext(Request request, Response response) {
        this(request, response, null);
    }

    public WebContext(Request request, Response response, BodyValidator validator) {
        this.request = request;
        this.response = response;
        this.validator = validator;
    }

    public Request request() {
        return request;
    }

    public Response response() {
        return response;
    }

    /**
     * Parses the body and performs validation if a validator is present.
     */
    public <T> T body(Class<T> type) {
        T body = request.body(type);
        if (body != null && validator != null && validator.supports(type)) {
            ValidationResult result = validator.validate(body);
            if (!result.isValid()) {
                throw new RuntimeException("Validation failed: " + String.join(", ", result.getErrors()));
            }
        }
        return body;
    }

    // Shortcut methods for convenience
    public String path() {
        return request.getPath();
    }

    public String method() {
        return request.getMethod();
    }

    public void ok(Object result) {
        response.ok(result);
    }

    public void notFound() {
        response.notFound();
    }

    public void error(Exception e) {
        response.error(e);
    }
}
