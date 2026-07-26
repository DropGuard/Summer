package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.web.BodyConverter;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.ServerConfig;
import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.websocket.WsInterceptor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketUpgradeHandler {

	private static final Logger log = LoggerFactory.getLogger(WebSocketUpgradeHandler.class);

	private final WsRouter wsRouter;
	private final ServerConfig config;
	private final List<WsInterceptor> wsInterceptors;
	private final BodyConverter jsonConverter;

	public WebSocketUpgradeHandler(WsRouter wsRouter, ServerConfig config, List<WsInterceptor> wsInterceptors,
			BodyConverter jsonConverter) {
		this.wsRouter = wsRouter;
		this.config = config;
		this.wsInterceptors = wsInterceptors;
		this.jsonConverter = jsonConverter;
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

		java.util.Map<String, String> headers = new java.util.HashMap<>();
		for (java.util.Map.Entry<String, String> entry : nettyReq.headers()) {
			headers.put(entry.getKey().toLowerCase(), entry.getValue());
		}
		NettyWebSocketContext wsContext = new NettyWebSocketContext(nettyCtx, wsMatch.pathParams(), headers,
				wsInterceptors, jsonConverter);
		wsMatch.handler().handle(wsContext);

		c.setHandled(true);

		FullHttpRequest retainedReq = nettyReq.retain();
		final String upgradePath = path;
		nettyCtx.executor().execute(() -> {
			// Logs upgrade failures (e.g. an invalid handshake that Netty rejects
			// with a 500 of its own) on the server side. Nothing is sent to the
			// client beyond Netty's standard response — this is purely diagnostic
			// so a red WS IT can be triaged from the server log, not guessed at.
			nettyCtx.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {
				@Override
				public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
					log.error("WebSocket upgrade failed for path={}", upgradePath, cause);
					ctx.close();
				}
			});
			nettyCtx.pipeline().addLast(new io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler(uri,
					true, config.maxWebSocketFrameSize()));
			nettyCtx.pipeline().addLast(new SummerWebSocketFrameHandler(wsContext, config.maxWebSocketFrameSize()));
			nettyCtx.fireChannelRead(retainedReq);
		});
		return true;
	}
}
