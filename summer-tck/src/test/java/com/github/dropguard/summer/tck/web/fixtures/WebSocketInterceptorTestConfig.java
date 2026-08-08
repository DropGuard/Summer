package com.github.dropguard.summer.tck.web.fixtures;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.web.websocket.WebSocketContext;
import com.github.dropguard.summer.web.websocket.WebSocketInterceptor;
import com.github.dropguard.summer.web.websocket.WebSocketInterceptorChain;

/**
 * WebSocket interceptor test configuration. The {@code /ws-test} route is provided by {@link
 * TestWsRouteProvider} (a {@code @Component}) so its bean name is the concrete class and does not
 * collide with other {@code WsRouteProvider} implementations (e.g. {@link ChatWsRouteProvider}) in
 * the test universe.
 */
@Configuration
public class WebSocketInterceptorTestConfig {
    public WebSocketInterceptorTestConfig() {}

    @Bean
    public WebSocketInterceptor testWsInterceptor() {
        return new WebSocketInterceptor() {
            @Override
            public void intercept(
                    WebSocketContext ctx, String message, WebSocketInterceptorChain chain) {
                String modifiedMessage = "[INTERCEPTED] " + message;
                chain.proceed(ctx, modifiedMessage);
            }
        };
    }
}
