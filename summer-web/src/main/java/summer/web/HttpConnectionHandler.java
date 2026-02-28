package summer.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

/**
 * Handles an individual HTTP TCP connection.
 * It reads the raw socket, parses the request, builds the middleware chain,
 * and dispatches the response.
 */
public class HttpConnectionHandler implements Runnable {
    private final Socket clientSocket;
    private final Router router;
    private final List<Middleware> middlewares;

    public HttpConnectionHandler(Socket clientSocket, Router router, List<Middleware> middlewares) {
        this.clientSocket = clientSocket;
        this.router = router;
        this.middlewares = middlewares;
    }

    @Override
    public void run() {
        try (
                InputStream input = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream()) {
            Request request = HttpRequestParser.parse(input);
            Response response = new Response(output);

            // Apply middleware chain
            Handler handler = createHandlerChain(request, response);
            handler.handle(request, response);
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

    private Handler createHandlerChain(Request request, Response response) {
        // Final handler that dispatches to router
        Handler dispatchHandler = (req, res) -> {
            try {
                Object result = router.route(req, res);
                if (result != null) {
                    res.ok(result); // Will serialize to JSON via JsonConverter if not a simple string
                } else {
                    res.notFound();
                }
            } catch (Exception e) {
                res.error(e);
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
