package summer.web.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import summer.validation.BodyValidator;
import summer.web.Router;
import summer.web.ServerConfig;
import summer.web.middleware.Middleware;

/**
 * Simple HTTP server implementation for Summer framework. This manages the TCP
 * ServerSocket and dispatches connections using virtual threads (Project Loom).
 *
 * <p>
 * Each incoming request is handled by a lightweight virtual thread, enabling
 * millions of concurrent connections without platform thread overhead.
 */
public class HttpServer {
	private final ServerConfig config;
	private final Router router;
	private final List<Middleware> middlewares;
	private final BodyValidator validator;
	private final List<BodyConverter> converters;
	private ServerSocket serverSocket;
	private volatile boolean running = false;
	
	/** Tracks active request processing for graceful shutdown. */
	private final AtomicInteger activeConnections = new AtomicInteger(0);

	public HttpServer(ServerConfig config, Router router, List<Middleware> middlewares) {
		this(config, router, middlewares, null, List.of());
	}

	public HttpServer(ServerConfig config, Router router, List<Middleware> middlewares, BodyValidator validator, List<BodyConverter> converters) {
		this.config = config;
		this.router = router;
		this.middlewares = middlewares;
		this.validator = validator;
		this.converters = converters;
	}

	public static HttpServer create(ServerConfig config, Router router, List<Middleware> middlewares, BodyValidator validator, List<BodyConverter> converters) {
		return new HttpServer(config, router, middlewares, validator, converters);
	}

	public AtomicInteger getActiveConnections() {
		return activeConnections;
	}

	public void start() throws IOException {
		serverSocket = new ServerSocket(config.port());
		running = true;
		System.out.println("Server started on port " + config.port());

		Thread.startVirtualThread(this::acceptConnections);
	}

	private void acceptConnections() {
		try {
			while (running) {
				Socket clientSocket = serverSocket.accept();
				handleClient(clientSocket);
			}
		} catch (IOException e) {
			if (running) {
				e.printStackTrace();
			}
		}
	}

	private void handleClient(Socket clientSocket) {
		Thread.startVirtualThread(new HttpConnectionHandler(clientSocket, config, router, middlewares, validator, converters, this));
	}

	public void stop() {
		running = false;
		try {
			if (serverSocket != null) {
				serverSocket.close();
				
				// Graceful shutdown: wait for active requests to finish
				System.out.println("Stopping server... waiting for " + activeConnections.get() + " active requests.");
				long maxWait = 30000; // 30 seconds
				long start = System.currentTimeMillis();
				while (activeConnections.get() > 0 && (System.currentTimeMillis() - start) < maxWait) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
				
				if (activeConnections.get() > 0) {
					System.out.println("Forcing shutdown: " + activeConnections.get() + " requests still active.");
				} else {
					System.out.println("Server stopped gracefully.");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int getPort() {
		return config.port();
	}
}