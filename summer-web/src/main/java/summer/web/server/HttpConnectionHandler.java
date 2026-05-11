package summer.web.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import summer.validation.BodyValidator;
import summer.web.Handler;
import summer.web.Request;
import summer.web.Response;
import summer.web.ServerConfig;
import summer.web.WebContext;
import summer.web.Router;
import summer.web.middleware.Middleware;

/**
 * Handles an individual HTTP TCP connection. It reads the raw socket, parses
 * the request, builds the middleware chain, and dispatches the response.
 */
public class HttpConnectionHandler implements Runnable {
	private final Socket clientSocket;
	private final ServerConfig config;
	private final Router router;
	private final List<Middleware> middlewares;
	private final BodyValidator validator;
	private final List<BodyConverter> converters;
	private final HttpServer server;

	public HttpConnectionHandler(Socket clientSocket, ServerConfig config, Router router, List<Middleware> middlewares) {
		this(clientSocket, config, router, middlewares, null, List.of(), null);
	}

	public HttpConnectionHandler(Socket clientSocket, ServerConfig config, Router router, List<Middleware> middlewares, BodyValidator validator, List<BodyConverter> converters, HttpServer server) {
		this.clientSocket = clientSocket;
		this.config = config;
		this.router = router;
		this.middlewares = middlewares;
		this.validator = validator;
		this.converters = converters;
		this.server = server;
	}

	@Override
	public void run() {
		if (server != null) server.getActiveConnections().incrementAndGet();
		try (InputStream input = clientSocket.getInputStream(); OutputStream output = clientSocket.getOutputStream()) {
			clientSocket.setSoTimeout(config.connectionTimeout()); 
			while (!clientSocket.isClosed()) {
				Request request;
				try {
					request = HttpRequestParser.parse(input, config.maxBodySize(), config.readTimeout());
				} catch (java.net.SocketTimeoutException e) {
					// Idle Keep-Alive connection timed out, exit gracefully
					break;
				}
				
				if (request == null) break;

				Response response = new Response(output);
				WebContext ctx = new WebContext(request, response, validator, converters);

				// Determine if we should keep the connection alive
				String connectionHeader = request.getHeader("Connection");
				boolean keepAlive = connectionHeader != null && connectionHeader.equalsIgnoreCase("keep-alive");
				
				if (keepAlive) {
					response.setHeader("Connection", "keep-alive");
				} else {
					response.setHeader("Connection", "close");
				}

				// Execute handler chain
				try {
					Handler handler = createHandlerChain(ctx);
					handler.handle(ctx);
				} catch (Exception e) {
					if (!response.isCommitted()) {
						response.error(e);
					}
				}
				
				// Ensure response is sent if not already committed (default to 404)
				if (!response.isCommitted()) {
					response.notFound(); 
				}

				if (!keepAlive) break;
			}
		} catch (Exception e) {
			// Connection reset or other IO issues are common during socket handling
		} finally {
			if (server != null) server.getActiveConnections().decrementAndGet();
			try {
				if (!clientSocket.isClosed()) {
					clientSocket.close();
				}
			} catch (IOException e) {
				// Ignore
			}
		}
	}

	private Handler createHandlerChain(WebContext ctx) {
		// Final handler that dispatches to router
		Handler dispatchHandler = (c) -> {
			try {
				Object result = router.route(c);
				if (result != null) {
					c.ok(result); // Will serialize to JSON via JsonConverter if not a simple string
				} else {
					c.notFound();
				}
			} catch (Exception e) {
				c.error(e);
			}
			return null;
		};

		// Apply middleware chain
		Handler handler = dispatchHandler;
		for (Middleware middleware : middlewares) {
			handler = middleware.apply(handler);
		}

		return handler;
	}
}
