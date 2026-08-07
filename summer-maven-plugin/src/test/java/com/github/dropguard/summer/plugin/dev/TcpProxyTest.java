package com.github.dropguard.summer.plugin.dev;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The dev-mode core: {@link TcpProxy}'s "eager kill, lazy compile" loop. A fake app server
 * increments its version on every restart; the proxy must hold the first request while dirty, boot
 * a fresh backend, and forward — so a second request (after a file change marked the proxy dirty)
 * reaches the new backend version.
 */
class TcpProxyTest {

    private static final class VersionedApp {
        final AtomicInteger bootCount = new AtomicInteger();

        /** Starts an HTTP server answering "v<n>" where n is the boot sequence. */
        HttpServer start(int port) throws IOException {
            int version = bootCount.incrementAndGet();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext(
                    "/",
                    exchange -> {
                        byte[] body = ("v" + version).getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(body);
                        }
                    });
            server.start();
            return server;
        }
    }

    @Test
    void lazyReloadForwardsToFreshBackendAfterChange() throws Exception {
        VersionedApp app = new VersionedApp();
        AtomicReference<HttpServer> currentServer = new AtomicReference<>();

        // Fake AppProcessManager: boot a new versioned server on the requested port.
        AppProcessManager fakeManager =
                new AppProcessManager() {
                    @Override
                    public void start(int port, String mainClass, String classpath) {
                        try {
                            currentServer.set(app.start(port));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void kill() {
                        HttpServer server = currentServer.getAndSet(null);
                        if (server != null) server.stop(0);
                    }
                };

        HotCompiler noopCompiler = new HotCompiler("", null);
        TcpProxy proxy = new TcpProxy(0, noopCompiler, null, fakeManager, "fake.Main");

        proxy.start();
        int publicPort = proxy.publicPort();

        HttpClient client = HttpClient.newHttpClient();

        // 1. First request: proxy is dirty from startup — boots backend v1 and forwards.
        String first = get(client, publicPort);
        assertEquals("v1", first, "first request must reach the initial backend");

        // 2. Simulate a source change the way DirectoryWatcher does: EAGER KILL first (breaks
        // any keep-alive pipe so the next request opens a fresh connection and hits the lazy
        // reload barrier), then the dirty flag + the changed file.
        fakeManager.kill();
        proxy.changedFiles.add(new java.io.File("fake/App.java"));
        proxy.isDirty = true;

        // 3. Next request: held while the proxy reboots; reaches backend v2.
        String second = get(client, publicPort);
        assertEquals("v2", second, "request after a change must reach the fresh backend");

        fakeManager.kill();
    }

    private static String get(HttpClient client, int port) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return response.body();
    }
}
