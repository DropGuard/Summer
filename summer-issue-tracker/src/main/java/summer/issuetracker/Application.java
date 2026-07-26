package summer.issuetracker;

import summer.boot.SummerApplication;
import summer.web.middleware.CorsMiddleware;
import summer.issuetracker.security.JwtAuthMiddleware;

/**
 * Entry point for the Issue Tracker demo. Applies the demo's auth + the
 * framework-provided CORS middleware, then starts the Netty server.
 */
public class Application {
    public static void main(String[] args) throws Exception {
        new SummerApplication()
                .apply(CorsMiddleware.class)
                .apply(JwtAuthMiddleware.class)
                .start(args);
    }
}
