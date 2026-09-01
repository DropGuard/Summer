package com.github.dropguard.summer.twitter;

import com.github.dropguard.summer.boot.SummerApplication;
import com.github.dropguard.summer.twitter.auth.AuthMiddleware;
import com.github.dropguard.summer.web.middleware.CorsMiddleware;

public class Application {
    public static void main(String[] args) throws Exception {
        new SummerApplication().apply(CorsMiddleware.class).apply(AuthMiddleware.class).start(args);
    }
}
