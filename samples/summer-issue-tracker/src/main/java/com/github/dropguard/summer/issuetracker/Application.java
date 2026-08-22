package com.github.dropguard.summer.issuetracker;

import com.github.dropguard.summer.boot.SummerApplication;
import com.github.dropguard.summer.issuetracker.security.JwtAuthMiddleware;
import com.github.dropguard.summer.web.middleware.CorsMiddleware;

/**
 * Entry point for the Issue Tracker demo. Applies the demo's auth + the framework-provided CORS
 * middleware, then starts the Netty server.
 */
public class Application {
    public static void main(String[] args) throws Exception {
        new SummerApplication()
                .apply(CorsMiddleware.class)
                .apply(JwtAuthMiddleware.class)
                .start(args);
    }
}
