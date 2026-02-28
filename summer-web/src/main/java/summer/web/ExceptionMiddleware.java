package summer.web;

/**
 * Global exception middleware that handles exceptions during request processing.
 */
public class ExceptionMiddleware implements Middleware {
    @Override
    public Handler apply(Handler handler) {
        return (request, response) -> {
            try {
                return handler.handle(request, response);
            } catch (Exception e) {
                response.error(e);
                System.err.println("Request failed: " + request.getPath());
                e.printStackTrace();
                return null;
            }
        };
    }
}