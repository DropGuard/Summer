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
import summer.web.WebContext;
import summer.web.Router;
import summer.web.middleware.Middleware;

/**
 * Handles an individual HTTP TCP connection. It reads the raw socket, parses
 * the request, builds the middleware chain, and dispatches the response.
 */
public class HttpConnectionHandler implements Runnable {
	private final Socket clientSocket;
	private final Router router;
	private final List<Middleware> middlewares;
	private final BodyValidator validator;

	public HttpConnectionHandler(Socket clientSocket, Router router, List<Middleware> middlewares) {
		this(clientSocket, router, middlewares, null);
	}

	public HttpConnectionHandler(Socket clientSocket, Router router, List<Middleware> middlewares, BodyValidator validator) {
		this.clientSocket = clientSocket;
		this.router = router;
		this.middlewares = middlewares;
		this.validator = validator;
	}

	@Override
	public void run() {
		try (InputStream input = clientSocket.getInputStream(); OutputStream output = clientSocket.getOutputStream()) {
			Request request = HttpRequestParser.parse(input);
			if (request == null) return;

			Response response = new Response(output);
			WebContext ctx = new WebContext(request, response, validator);

			// Apply middleware chain
			Handler handler = createHandlerChain(ctx);
			handler.handle(ctx);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (!clientSocket.isClosed()) {
					clientSocket.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
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
