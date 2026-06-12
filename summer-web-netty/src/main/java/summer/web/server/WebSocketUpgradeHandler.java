package summer.web.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.ServerConfig;
import summer.web.WsRouter;
import summer.web.websocket.WsInterceptor;

public class WebSocketUpgradeHandler {

	private static final Logger log = LoggerFactory.getLogger(WebSocketUpgradeHandler.class);

	private final WsRouter wsRouter;
	private final ServerConfig config;
	private final List<WsInterceptor> wsInterceptors;

	public WebSocketUpgradeHandler(WsRouter wsRouter, ServerConfig config, List<WsInterceptor> wsInterceptors) {
		this.wsRouter = wsRouter;
		this.config = config;
		this.wsInterceptors = wsInterceptors;
	}

	public boolean isWebSocketUpgrade(FullHttpRequest nettyReq) {
		return nettyReq.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true);
	}

	public boolean handleUpgrade(ChannelHandlerContext nettyCtx, FullHttpRequest nettyReq, HttpContext c) {
		String uri = nettyReq.uri();
		String path = uri;
		int questionMarkIndex = uri.indexOf('?');
		if (questionMarkIndex != -1) {
			path = uri.substring(0, questionMarkIndex);
		}

		String origin = nettyReq.headers().get(HttpHeaderNames.ORIGIN);
		String host = nettyReq.headers().get(HttpHeaderNames.HOST);
		if (!config.isOriginAllowed(origin, host)) {
			log.warn("WebSocket connection rejected: origin '{}' not allowed for host '{}'", origin, host);
			c.status(HttpStatus.FORBIDDEN);
			c.text(HttpStatus.FORBIDDEN, "Origin not allowed");
			return false;
		}

		WsRouter.WsMatch wsMatch = wsRouter.routeWs(path);
		if (wsMatch == null) {
			return false;
		}

		NettyWebSocketContext wsContext = new NettyWebSocketContext(nettyCtx, wsMatch.pathParams(), wsInterceptors);
		wsMatch.handler().handle(wsContext);

		c.setHandled(true);

		FullHttpRequest retainedReq = nettyReq.retain();
		nettyCtx.executor().execute(() -> {
			nettyCtx.pipeline().addLast(
					new io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler(uri, null, true,
							config.maxWebSocketFrameSize()));
			nettyCtx.pipeline()
					.addLast(new SummerWebSocketFrameHandler(wsContext, config.maxWebSocketFrameSize()));
			nettyCtx.fireChannelRead(retainedReq);
		});
		return true;
	}
}
