package summer.fixtures;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.web.WsRouteProvider;
import summer.web.server.NettyWebSocketBroadcaster;

@Configuration
public class WsTestConfig {
	public WsTestConfig() {
	}

	@Bean
	public WsRouteProvider wsRouteProvider(NettyWebSocketBroadcaster broadcaster) {
		return builder -> builder.ws("/chat/{room}", ctx -> {
			String room = ctx.pathParam("room");
			broadcaster.join(room, ctx);

			ctx.onMessage(msg -> {
				if (msg.startsWith("BROADCAST:")) {
					broadcaster.broadcast(room, msg.substring(10));
				}
			});

			ctx.onClose(() -> {
				broadcaster.leave(room, ctx);
			});
		});
	}
}
