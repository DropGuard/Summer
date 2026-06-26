package summer.fixtures;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.web.WsRouteProvider;
import summer.web.websocket.WebSocketContext;
import summer.web.websocket.WsFilterChain;
import summer.web.websocket.WsInterceptor;

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

	@Bean
	public WsRouteProvider wsRouteProvider() {
		return builder -> builder.ws("/ws-test", ctx -> {
			ctx.onMessage(msg -> {
				ctx.send(msg);
			});
		});
	}
}
