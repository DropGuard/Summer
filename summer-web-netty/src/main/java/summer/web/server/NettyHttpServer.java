package summer.web.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ApplicationContext;
import summer.web.BodyConverter;
import summer.web.HttpRouter;
import summer.web.JsonBodyConverter;
import summer.web.Middleware;
import summer.web.ServerConfig;
import summer.web.WsRouter;
import summer.web.annotation.GlobalMiddleware;

public class NettyHttpServer {
	private static final Logger log = LoggerFactory.getLogger(NettyHttpServer.class);

	private final ServerConfig config;
	private final HttpRouter httpRouter;
	private final WsRouter wsRouter;
	private final List<Middleware> middlewares;
	private final BodyConverter jsonConverter;

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private ChannelFuture serverChannelFuture;
	private final summer.web.ExceptionRegistry exceptionRegistry;
	private final List<summer.web.websocket.WsInterceptor> wsInterceptors;

	private final AtomicInteger activeConnections = new AtomicInteger(0);

	public NettyHttpServer(ServerConfig config, HttpRouter httpRouter, WsRouter wsRouter, List<Middleware> middlewares,
			BodyConverter jsonConverter, summer.web.ExceptionRegistry exceptionRegistry,
			List<summer.web.websocket.WsInterceptor> wsInterceptors) {
		this.config = config;
		this.httpRouter = httpRouter;
		this.wsRouter = wsRouter;
		this.middlewares = middlewares;
		this.jsonConverter = jsonConverter;
		this.exceptionRegistry = exceptionRegistry;
		this.wsInterceptors = wsInterceptors;
	}

	/**
	 * Creates a NettyHttpServer by assembling components from the application
	 * context.
	 */
	public static NettyHttpServer create(ApplicationContext context, ServerConfig config, HttpRouter httpRouter,
			WsRouter wsRouter, summer.web.ExceptionRegistry exceptionRegistry) {
		List<Middleware> globalMiddlewares = getGlobalMiddlewares(context);
		List<Middleware> middlewares = new java.util.ArrayList<>(globalMiddlewares);

		BodyConverter jsonConverter = findOptionalBean(context, BodyConverter.class);
		if (jsonConverter == null) {
			jsonConverter = new JsonBodyConverter();
		}

		List<summer.web.websocket.WsInterceptor> wsInterceptors = context
				.getBeans(summer.web.websocket.WsInterceptor.class);
		return new NettyHttpServer(config, httpRouter, wsRouter, middlewares, jsonConverter, exceptionRegistry,
				wsInterceptors);
	}

	/**
	 * Discovers all global middlewares from the application context. A middleware
	 * is global if its class is annotated with {@link GlobalMiddleware}.
	 */
	private static List<Middleware> getGlobalMiddlewares(ApplicationContext context) {
		return context.getBeans(Middleware.class).stream()
				.filter(m -> m.getClass().isAnnotationPresent(GlobalMiddleware.class)).toList();
	}

	private static <T> T findOptionalBean(ApplicationContext context, Class<T> type) {
		try {
			return context.getBean(type);
		} catch (Exception e) {
			return null;
		}
	}

	public AtomicInteger getActiveConnections() {
		return activeConnections;
	}

	public int getPort() {
		if (serverChannelFuture != null
				&& serverChannelFuture.channel().localAddress() instanceof java.net.InetSocketAddress) {
			return ((java.net.InetSocketAddress) serverChannelFuture.channel().localAddress()).getPort();
		}
		return config.port();
	}

	public void start() {
		bossGroup = new NioEventLoopGroup(1); // 1 thread to accept connections
		workerGroup = new NioEventLoopGroup(); // defaults to CPU cores * 2 for I/O

		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel ch) {
							ch.pipeline().addLast(new HttpServerCodec())
									.addLast(new HttpObjectAggregator(config.maxBodySize())) // Combine HTTP parts into
																								// FullHttpRequest
									.addLast(
											new NettyHttpServerHandler(httpRouter, wsRouter, middlewares, jsonConverter,
													NettyHttpServer.this, config, exceptionRegistry, wsInterceptors));
						}
					}).option(ChannelOption.SO_BACKLOG, 1024).childOption(ChannelOption.TCP_NODELAY, true)
					.childOption(ChannelOption.SO_KEEPALIVE, true);

			serverChannelFuture = b.bind(config.port()).sync();
			log.info("Netty Server started on port {}", config.port());

		} catch (InterruptedException e) {
			log.error("Netty server interrupted during startup", e);
			Thread.currentThread().interrupt();
		}
	}

	public void stop() {
		stop(java.time.Duration.ZERO);
	}

	public void stop(java.time.Duration timeout) {
		log.info("Stopping Netty server... {} active requests.", activeConnections.get());

		try {
			// 1. Stop accepting new connections
			if (serverChannelFuture != null) {
				serverChannelFuture.channel().close().sync();
			}

			// 2. Wait for virtual threads (in-flight requests) to complete
			if (!timeout.isZero() && activeConnections.get() > 0) {
				log.info("Waiting up to {} for {} active requests to finish...", timeout, activeConnections.get());
				long deadline = System.nanoTime() + timeout.toNanos();
				while (activeConnections.get() > 0 && System.nanoTime() < deadline) {
					Thread.sleep(50);
				}
				if (activeConnections.get() > 0) {
					log.warn("{} active requests still open after timeout.", activeConnections.get());
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		// 3. Shutdown Netty event loops
		if (bossGroup != null) {
			bossGroup.shutdownGracefully();
		}
		if (workerGroup != null) {
			workerGroup.shutdownGracefully();
		}

		log.info("Netty Server stopped.");
	}
}
