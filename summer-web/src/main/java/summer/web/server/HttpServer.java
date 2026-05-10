package summer.web.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import summer.web.Router;
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
	private final int port;
	private final Router router;
	private final List<Middleware> middlewares;
	private ServerSocket serverSocket;
	private volatile boolean running = false;

	public HttpServer(int port, Router router, List<Middleware> middlewares) {
		this.port = port;
		this.router = router;
		this.middlewares = middlewares;
	}

	public static HttpServer create(int port, Router router) {
		return new HttpServer(port, router, List.of());
	}

	public static HttpServer create(int port, Router router, List<Middleware> middlewares) {
		return new HttpServer(port, router, middlewares);
	}

	public void start() throws IOException {
		serverSocket = new ServerSocket(port);
		running = true;
		System.out.println("Server started on port " + port);

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
		Thread.startVirtualThread(new HttpConnectionHandler(clientSocket, router, middlewares));
	}

	public void stop() {
		running = false;
		try {
			if (serverSocket != null) {
				serverSocket.close();
				System.out.println("Server stopped");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int getPort() {
		return port;
	}
}