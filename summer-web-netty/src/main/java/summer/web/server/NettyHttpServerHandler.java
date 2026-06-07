package summer.web.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.web.BodyConverter;
import summer.web.Handler;
import summer.web.HttpContext;
import summer.web.HttpRouter;
import summer.web.HttpStatus;
import summer.web.Middleware;
import summer.web.Request;
import summer.web.ServerConfig;
import summer.web.WsRouter;

public class NettyHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private static final Logger log = LoggerFactory.getLogger(NettyHttpServerHandler.class);

	private final HttpRouter httpRouter;
	private final WsRouter wsRouter;
	private final List<Middleware> middlewares;
	private final BodyConverter jsonConverter;
	private final NettyHttpServer server;
	private final ServerConfig config;
	private final summer.web.ExceptionRegistry exceptionRegistry;
	private final List<summer.web.websocket.WsInterceptor> wsInterceptors;

	public NettyHttpServerHandler(HttpRouter httpRouter, WsRouter wsRouter, List<Middleware> middlewares,
			BodyConverter jsonConverter, NettyHttpServer server, ServerConfig config,
			summer.web.ExceptionRegistry exceptionRegistry, List<summer.web.websocket.WsInterceptor> wsInterceptors) {
		this.httpRouter = httpRouter;
		this.wsRouter = wsRouter;
		this.middlewares = middlewares;
		this.jsonConverter = jsonConverter;
		this.server = server;
		this.config = config;
		this.exceptionRegistry = exceptionRegistry;
		this.wsInterceptors = wsInterceptors;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest nettyReq) {
		if (server != null)
			server.getActiveConnections().incrementAndGet();

		// 1. Check HTTP Keep-Alive
		boolean keepAlive = HttpUtil.isKeepAlive(nettyReq);

		// 2. Retain the request because we are handing it off to another thread
		nettyReq.retain();

		// 3. Dispatch to Virtual Thread (Loom)
		Thread.startVirtualThread(() -> {
			try {
				processRequest(ctx, nettyReq, keepAlive);
			} finally {
				// Must release the retained Netty buffer once done processing
				nettyReq.release();
				if (server != null)
					server.getActiveConnections().decrementAndGet();
			}
		});
	}

	private void sendSimpleResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
		byte[] bytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
				Unpooled.wrappedBuffer(bytes));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
		response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private void processRequest(ChannelHandlerContext ctx, FullHttpRequest nettyReq, boolean keepAlive) {
		try {
			Request request = NettyRequestAdapter.adapt(nettyReq);
			HttpContext webCtx = new HttpContext(request, jsonConverter);

			Handler handler = createHandlerChain(ctx, nettyReq);
			handler.handle(webCtx);

			if (webCtx.isHandled()) {
				return;
			}

			if (webCtx.statusCode() == null) {
				webCtx.text(HttpStatus.NOT_FOUND, "Not Found");
			}

			sendResponse(ctx, webCtx, keepAlive);

		} catch (Exception e) {
			log.error("Fatal framework error", e);
			sendErrorResponse(ctx, keepAlive);
		}
	}

	private Handler createHandlerChain(ChannelHandlerContext nettyCtx, FullHttpRequest nettyReq) {
		Handler dispatchHandler = (c) -> {
			try {
				if (nettyReq.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)) {
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
						return null;
					}

					WsRouter.WsMatch wsMatch = wsRouter.routeWs(path);
					if (wsMatch != null) {
						NettyWebSocketContext wsContext = new NettyWebSocketContext(nettyCtx, wsMatch.pathParams(),
								wsInterceptors);
						wsMatch.handler().handle(wsContext);

						c.setHandled(true);

						FullHttpRequest retainedReq = nettyReq.retain();
						nettyCtx.executor().execute(() -> {
							nettyCtx.pipeline().addLast(
									new io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler(uri, null,
											true, config.maxWebSocketFrameSize()));
							nettyCtx.pipeline().addLast(
									new SummerWebSocketFrameHandler(wsContext, config.maxWebSocketFrameSize()));
							nettyCtx.fireChannelRead(retainedReq);
						});
						return null;
					}
				}

				httpRouter.route(c);
			} catch (Exception e) {
				if (exceptionRegistry != null) {
					Handler customHandler = exceptionRegistry.getHandler(e);
					if (customHandler != null) {
						try {
							c.request().setAttribute("last_exception", e);
							return customHandler.handle(c);
						} catch (Exception ex) {
							e = ex;
						}
					}
				}

				if (e instanceof summer.web.exception.SummerWebException webEx) {
					c.status(webEx.statusCode());
					if (webEx.getMessage() != null) {
						c.text(webEx.statusCode(), webEx.getMessage());
					}
					return null;
				}

				c.error(e);
				log.error("Request failed: {}", c.request().getPath(), e);
				return null;
			}
			return null;
		};

		Handler handler = dispatchHandler;
		for (Middleware middleware : middlewares) {
			handler = middleware.apply(handler);
		}

		return handler;
	}

	private void sendResponse(ChannelHandlerContext ctx, HttpContext webCtx, boolean keepAlive) {
		HttpResponseStatus status = HttpResponseStatus.valueOf(webCtx.statusCode().code());

		FullHttpResponse nettyResp;
		if (webCtx.resultObject() != null && webCtx.converter() != null) {
			nettyResp = serializeResponse(ctx, webCtx, status, keepAlive);
			if (nettyResp == null) {
				return; // Error response already sent
			}
		} else if (webCtx.body() != null && webCtx.body().length > 0) {
			nettyResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
					Unpooled.wrappedBuffer(webCtx.body()));
		} else {
			nettyResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
		}

		// Apply headers
		for (Map.Entry<String, String> entry : webCtx.headers().entrySet()) {
			nettyResp.headers().set(entry.getKey(), entry.getValue());
		}

		if (keepAlive) {
			if (!nettyResp.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
				nettyResp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, nettyResp.content().readableBytes());
			}
			nettyResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			ctx.writeAndFlush(nettyResp);
		} else {
			nettyResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
			ctx.writeAndFlush(nettyResp).addListener(ChannelFutureListener.CLOSE);
		}
	}

	private FullHttpResponse serializeResponse(ChannelHandlerContext ctx, HttpContext webCtx, HttpResponseStatus status,
			boolean keepAlive) {
		io.netty.buffer.ByteBuf buf = ctx.alloc().directBuffer();
		try (io.netty.buffer.ByteBufOutputStream out = new io.netty.buffer.ByteBufOutputStream(buf)) {
			webCtx.converter().writeToStream(webCtx.resultObject(), out);
		} catch (Exception e) {
			buf.release();
			log.error("Serialization error", e);
			sendErrorResponse(ctx, keepAlive);
			return null;
		}
		return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
	}

	private FullHttpResponse sendErrorResponse(ChannelHandlerContext ctx, boolean keepAlive) {
		byte[] errorBytes = "Internal Server Error".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		FullHttpResponse errorResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
				HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.wrappedBuffer(errorBytes));
		errorResp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
		errorResp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, errorBytes.length);
		if (keepAlive) {
			errorResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			ctx.writeAndFlush(errorResp);
		} else {
			errorResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
			ctx.writeAndFlush(errorResp).addListener(ChannelFutureListener.CLOSE);
		}
		return errorResp;
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		log.error("Channel exception", cause);
		try {
			ctx.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}