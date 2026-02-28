package summer.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Simple HTTP server implementation for Summer framework.
 * This is the entry point for handling HTTP requests.
 */
public class HttpServer {
    private final int port;
    private final Router router;
    private final List<Middleware> middlewares;
    private ServerSocket serverSocket;
    private final Map<String, BiFunction<Request, Response, Object>> routeHandlers = new ConcurrentHashMap<>();
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

        new Thread(this::acceptConnections).start();
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
        new Thread(() -> {
            try (
                InputStream input = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream()
            ) {
                Request request = createRequest(input);
                Response response = new Response(output);

                // Apply middleware chain
                Handler handler = createHandlerChain(request, response);
                handler.handle(request, response);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private Request createRequest(InputStream input) {
        // Simple request parsing (for demonstration purposes only)
        String method = "GET";
        String path = "/";
        String query = "";
        byte[] body = new byte[0];

        return new Request(method, path, query, body);
    }

    private Handler createHandlerChain(Request request, Response response) {
        // Final handler that dispatches to router
        Handler dispatchHandler = (req, res) -> {
            try {
                Object result = router.route(req, res);
                if (result != null) {
                    res.ok(result.toString());
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