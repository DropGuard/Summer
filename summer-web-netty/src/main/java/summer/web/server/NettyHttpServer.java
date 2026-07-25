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
import summer.core.BeanContainer;
import summer.core.exception.NoSuchBeanException;
import summer.web.BodyConverter;
import summer.web.HttpRouter;
import summer.web.JsonBodyConverter;
import summer.web.Middleware;
import summer.web.ServerConfig;
import summer.web.WsRouter;

public class NettyHttpServer {
	private static final Logger log = LoggerFactory.getLogger(NettyHttpServer.class);

	private final ServerConfig config;
	private final WebServerDependencies deps;

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private ChannelFuture serverChannelFuture;

	private final AtomicInteger activeConnections = new AtomicInteger(0);

	public NettyHttpServer(ServerConfig config, WebServerDependencies deps) {
		this.config = config;
		this.deps = deps;
	}

	/**
	 * Creates a NettyHttpServer by assembling components from the application
	 * context.
	 */
	public static NettyHttpServer create(BeanContainer context, ServerConfig config, HttpRouter httpRouter,
			WsRouter wsRouter, summer.web.ExceptionRegistry exceptionRegistry,
			java.util.List<summer.web.Middleware> globalMiddlewares) {
		List<Middleware> middlewares = new java.util.ArrayList<>(globalMiddlewares);

		BodyConverter jsonConverter = findOptionalBean(context, BodyConverter.class);
		if (jsonConverter == null) {
			jsonConverter = new JsonBodyConverter();
		}

		List<summer.web.websocket.WsInterceptor> wsInterceptors = context
				.getBeans(summer.web.websocket.WsInterceptor.class);
		WebSocketUpgradeHandler wsUpgradeHandler = new WebSocketUpgradeHandler(wsRouter, config, wsInterceptors,
				jsonConverter);
		return new NettyHttpServer(config, new WebServerDependencies(httpRouter, wsRouter, middlewares, jsonConverter,
				exceptionRegistry, wsInterceptors, wsUpgradeHandler));
	}

	private static <T> T findOptionalBean(BeanContainer context, Class<T> type) {
		try {
			return context.getBean(type);
		} catch (NoSuchBeanException e) {
			return null;
		}
	}

	public AtomicInteger getActiveConnections() {
		return activeConnections;
	}

	private int getActualTargetPort() {
		String devPort = System.getenv("SUMMER_DEV_PORT");
		if (devPort != null) {
			return Integer.parseInt(devPort);
		}
		return config.port();
	}

	public int getPort() {
		if (serverChannelFuture != null
				&& serverChannelFuture.channel().localAddress() instanceof java.net.InetSocketAddress) {
			return ((java.net.InetSocketAddress) serverChannelFuture.channel().localAddress()).getPort();
		}
		return getActualTargetPort();
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
									.addLast(new NettyHttpServerHandler(NettyHttpServer.this, config, deps));
						}
					}).option(ChannelOption.SO_BACKLOG, 1024).option(ChannelOption.SO_REUSEADDR, true)
					.childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true);

			int targetPort = getActualTargetPort();
			serverChannelFuture = b.bind(targetPort).sync();

			if (System.getenv("SUMMER_DEV_PORT") != null) {
				log.info("[Summer] Dev proxy detected, redirecting actual bind port from {} to {}", config.port(),
						targetPort);
			}
			log.info("Netty Server started on port {}", targetPort);

		} catch (InterruptedException e) {
			log.error("Netty server interrupted during startup", e);
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Graceful shutdown in three stages: stop accepting new connections, wait up to
	 * {@code timeout} for in-flight requests to drain, then release the Netty event
	 * loops (awaited so the bound port is actually freed). Called by the runner's
	 * registered shutdown task; a zero timeout skips the drain wait.
	 */
	public void shutdown(java.time.Duration timeout) {
		log.info("Stopping Netty server... {} active requests.", activeConnections.get());
		// 1. Stop accepting new connections.
		try {
			if (serverChannelFuture != null) {
				serverChannelFuture.channel().close().sync();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		// 2. Wait for in-flight requests (virtual threads) to complete.
		if (!timeout.isZero() && activeConnections.get() > 0) {
			log.info("Waiting up to {} for {} active requests to finish...", timeout, activeConnections.get());
			long deadline = System.nanoTime() + timeout.toNanos();
			try {
				while (activeConnections.get() > 0 && System.nanoTime() < deadline) {
					Thread.sleep(50);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			if (activeConnections.get() > 0) {
				log.warn("{} active requests still open after timeout.", activeConnections.get());
			}
		}
		// 3. Shutdown Netty event loops — await so the bound port is actually
		// released (otherwise a back-to-back server start hits "Address already in
		// use").
		try {
			if (bossGroup != null) {
				bossGroup.shutdownGracefully().sync();
			}
			if (workerGroup != null) {
				workerGroup.shutdownGracefully().sync();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		log.info("Netty Server stopped.");
	}
}
