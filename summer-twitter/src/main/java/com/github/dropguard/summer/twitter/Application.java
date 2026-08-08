package com.github.dropguard.summer.twitter;

import com.github.dropguard.summer.boot.SummerApplication;

public class Application {
    public static void main(String[] args) throws Exception {
        new SummerApplication()
                .apply(com.github.dropguard.summer.web.middleware.CorsMiddleware.class)
                .apply(com.github.dropguard.summer.twitter.auth.AuthMiddleware.class)
                .start(args);
    }
}
