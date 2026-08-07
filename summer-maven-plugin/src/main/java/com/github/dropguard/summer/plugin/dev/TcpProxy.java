package com.github.dropguard.summer.plugin.dev;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Layer 4 TCP Proxy. Implements "Eager Kill, Lazy Compile" holding pattern. */
public class TcpProxy {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TcpProxy.class);
    private final int publicPort;
    private final HotCompiler compiler;
    private final JandexFastIndexer indexer;
    private final AppProcessManager appManager;
    private final String mainClass;

    // Concurrency control for lazy reload
    private final Object reloadLock = new Object();
    public volatile boolean isDirty = true; // True initially to force first boot
    public final Set<File> changedFiles =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    // The dynamic port assigned to the child JVM
    private volatile int currentBackendPort = 0;

    // The port the public ServerSocket actually bound (differs from publicPort when 0 was
    // requested, i.e. ephemeral — exposed for tests).
    private volatile int actualPort = 0;

    public TcpProxy(
            int publicPort,
            HotCompiler compiler,
            JandexFastIndexer indexer,
            AppProcessManager appManager,
            String mainClass) {
        this.publicPort = publicPort;
        this.compiler = compiler;
        this.indexer = indexer;
        this.appManager = appManager;
        this.mainClass = mainClass;
    }

    public void start() throws Exception {
        ServerSocket server = new ServerSocket(publicPort);
        actualPort = server.getLocalPort();
        log.info(
                "[Summer] TCP Proxy listening on :"
                        + publicPort
                        + " (Browser should connect here)");

        new Thread(
                        () -> {
                            while (true) {
                                try {
                                    Socket clientSocket = server.accept();
                                    handleClient(clientSocket);
                                } catch (Exception e) {
                                    log.error("Proxy accept error", e);
                                }
                            }
                        },
                        "Summer-TcpProxy-Acceptor")
                .start();
    }

    /** The actual bound port of the public socket (useful when {@code publicPort} was 0). */
    int publicPort() {
        return actualPort;
    }

    private void handleClient(Socket clientSocket) {
        new Thread(
                        () -> {
                            try {
                                // 1. LAZY RELOAD BARRIER
                                synchronized (reloadLock) {
                                    if (isDirty) {
                                        long reloadStart = System.nanoTime();
                                        List<File> filesToCompile = new ArrayList<>(changedFiles);
                                        changedFiles.clear();
                                        log.info(
                                                "[Summer] Code changed — reloading"
                                                        + (filesToCompile.isEmpty()
                                                                ? " (initial boot)"
                                                                : " ("
                                                                        + filesToCompile.size()
                                                                        + " file(s): "
                                                                        + filesToCompile.stream()
                                                                                .map(File::getName)
                                                                                .sorted()
                                                                                .collect(
                                                                                        java.util
                                                                                                .stream
                                                                                                .Collectors
                                                                                                .joining(
                                                                                                        ", "))
                                                                        + ")")
                                                        + "...");

                                        if (!filesToCompile.isEmpty()) {
                                            compiler.compile(filesToCompile);
                                            indexer.reindex(compiler.outputDir);
                                        }

                                        // Find a random free port for the backend
                                        try (ServerSocket s = new ServerSocket(0)) {
                                            currentBackendPort = s.getLocalPort();
                                        }

                                        appManager.start(
                                                currentBackendPort, mainClass, compiler.classpath);

                                        // Wait a tiny bit for Netty to bind in the child JVM
                                        Thread.sleep(500);
                                        isDirty = false;
                                        log.info(
                                                "[Summer] Backend ready on :"
                                                        + currentBackendPort
                                                        + " after "
                                                        + ((System.nanoTime() - reloadStart)
                                                                / 1_000_000)
                                                        + "ms. Releasing held connections.");
                                    }
                                }

                                // 2. CONNECT TO BACKEND
                                Socket backendSocket =
                                        connectWithRetry("127.0.0.1", currentBackendPort, 10);
                                if (backendSocket == null) {
                                    log.error("Failed to connect to backend child process.");
                                    clientSocket.close();
                                    return;
                                }

                                // 3. BIDIRECTIONAL PIPE
                                pipe(
                                        clientSocket.getInputStream(),
                                        backendSocket.getOutputStream(),
                                        clientSocket,
                                        backendSocket);
                                pipe(
                                        backendSocket.getInputStream(),
                                        clientSocket.getOutputStream(),
                                        backendSocket,
                                        clientSocket);

                            } catch (Exception e) {
                                closeQuietly(clientSocket);
                            }
                        },
                        "Summer-Proxy-Worker")
                .start();
    }

    private Socket connectWithRetry(String host, int port, int maxRetries) throws Exception {
        for (int i = 0; i < maxRetries; i++) {
            try {
                return new Socket(host, port);
            } catch (Exception e) {
                Thread.sleep(200); // Retry backoff as JVM boots
            }
        }
        return null;
    }

    private void pipe(InputStream in, OutputStream out, Socket s1, Socket s2) {
        new Thread(
                        () -> {
                            try {
                                byte[] buffer = new byte[8192];
                                int n;
                                while ((n = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, n);
                                    out.flush();
                                }
                            } catch (Exception e) {
                                // Pipe broken (e.g. JVM killed by Watcher), silent exit
                            } finally {
                                closeQuietly(s1);
                                closeQuietly(s2);
                            }
                        })
                .start();
    }

    private void closeQuietly(Socket s) {
        try {
            if (s != null) s.close();
        } catch (Exception ignored) {
        }
    }
}
