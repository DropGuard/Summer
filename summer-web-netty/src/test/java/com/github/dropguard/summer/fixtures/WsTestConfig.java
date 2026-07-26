package com.github.dropguard.summer.fixtures;

import com.github.dropguard.summer.core.annotation.Configuration;

/**
 * WebSocket test configuration. The {@code /chat/{room}} route is provided by
 * {@link ChatWsRouteProvider} (a {@code @Component}) so its bean name is the
 * concrete class and does not collide with other {@code WsRouteProvider}
 * implementations in the test universe.
 */
@Configuration
public class WsTestConfig {
	public WsTestConfig() {
	}
}
