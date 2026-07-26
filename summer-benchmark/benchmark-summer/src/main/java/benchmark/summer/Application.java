package com.github.dropguard.summer.benchmark;


import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.boot.SummerApplication;

@Configuration
public class Application {
    public static void main(String[] args) throws Exception {
        new SummerApplication().start(args);
    }

    @com.github.dropguard.summer.core.annotation.Bean
    public UserService userService() {
        return new UserService();
    }
}
