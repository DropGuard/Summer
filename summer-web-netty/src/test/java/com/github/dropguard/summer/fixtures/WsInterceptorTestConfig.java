package com.github.dropguard.summer.fixtures;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.web.websocket.WebSocketContext;
import com.github.dropguard.summer.web.websocket.WsFilterChain;
import com.github.dropguard.summer.web.websocket.WsInterceptor;

/**
 * WebSocket interceptor test configuration. The {@code /ws-test} route is
 * provided by {@link TestWsRouteProvider} (a {@code @Component}) so its bean
 * name is the concrete class and does not collide with other
 * {@code WsRouteProvider} implementations (e.g. {@link ChatWsRouteProvider}) in
 * the test universe.
 */
@Configuration
public class WsInterceptorTestConfig {
	public WsInterceptorTestConfig() {
	}

	@Bean
	public WsInterceptor testWsInterceptor() {
		return new WsInterceptor() {
			@Override
			public void intercept(WebSocketContext ctx, String message, WsFilterChain chain) {
				String modifiedMessage = "[INTERCEPTED] " + message;
				chain.doFilter(ctx, modifiedMessage);
			}
		};
	}
}
