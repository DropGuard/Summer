package com.github.dropguard.summer.grpc.server;

import com.github.dropguard.summer.core.ApplicationRunner;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.config.ShutdownConfig;
import com.github.dropguard.summer.grpc.config.GrpcServerConfig;
import com.github.dropguard.summer.grpc.config.GrpcTlsConfig;
import com.github.dropguard.summer.grpc.exception.SummerGrpcException;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC server runner that starts the gRPC server.
 *
 * <p>This is a framework infrastructure bean provided by {@code GrpcInfrastructureConfiguration}.
 */
@Internal
public class GrpcServerRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerRunner.class);

    private final GrpcTlsConfig tlsConfig;
    private final GrpcServerConfig serverConfig;
    private final ShutdownConfig shutdownConfig;
    private Server server;

    public GrpcServerRunner(
            GrpcTlsConfig tlsConfig, GrpcServerConfig serverConfig, ShutdownConfig shutdownConfig) {
        this.tlsConfig = tlsConfig;
        this.serverConfig = serverConfig;
        this.shutdownConfig = shutdownConfig;
    }

    public int getPort() {
        return server != null ? server.getPort() : -1;
    }

    @Override
    public void run(BeanContainer context) throws Exception {
        List<BindableService> services = context.getBeans(BindableService.class);

        if (services.isEmpty()) {
            return; // No gRPC services to expose
        }

        int port = serverConfig.port();
        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(port);

        List<ServerInterceptor> interceptors = context.getBeans(ServerInterceptor.class);
        for (ServerInterceptor interceptor : interceptors) {
            serverBuilder.intercept(interceptor);
            log.info("gRPC Interceptor registered: {}", interceptor.getClass().getSimpleName());
        }
        // Add GrpcExceptionInterceptor last so it acts as the outermost boundary
        serverBuilder.intercept(new GrpcExceptionInterceptor());

        // Configure TLS if enabled and certificates are provided
        if (tlsConfig.enabled()
                && tlsConfig.certChain() != null
                && tlsConfig.privateKey() != null) {
            File certChainFile = new File(tlsConfig.certChain());
            File privateKeyFile = new File(tlsConfig.privateKey());
            serverBuilder.useTransportSecurity(certChainFile, privateKeyFile);
            log.info("gRPC TLS enabled with cert: {}", tlsConfig.certChain());
        } else {
            log.warn("gRPC TLS disabled - using plaintext (not recommended for production)");
        }

        for (BindableService service : services) {
            serverBuilder.addService(service);
            log.info("Route registered (gRPC): {}", service.getClass().getSimpleName());
        }

        this.server = serverBuilder.build();

        try {
            this.server.start();
            log.info("gRPC Server started on port {}", this.server.getPort());
        } catch (IOException e) {
            throw new SummerGrpcException(
                    io.grpc.Status.INTERNAL.withDescription(
                            "Failed to start gRPC Server on port " + port),
                    e);
        }

        context.addShutdownTask(
                () -> shutdown(java.time.Duration.ofMillis(shutdownConfig.timeoutMs())));
    }

    private void shutdown(java.time.Duration timeout) {
        if (server == null || server.isShutdown()) {
            return;
        }
        log.info("Shutting down gRPC Server...");
        // gRPC initiates graceful shutdown: rejects new calls, lets in-flight
        // ones finish, then terminates.
        server.shutdown();
        try {
            if (timeout.isZero()) {
                // Zero drain timeout: the caller asked for an immediate stop — force
                // termination rather than blocking on an unbounded awaitTermination().
                server.shutdownNow();
                server.awaitTermination();
                return;
            }
            if (!server.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                // Graceful drain exceeded the timeout — force-terminate remaining calls,
                // then wait for the (now bounded) termination to complete.
                server.shutdownNow();
                server.awaitTermination();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("gRPC Server stopped");
    }

    /**
     * Convenience for direct/test use: stops the server immediately (zero drain timeout). The
     * container drives the same staging via the shutdown task registered in {@link
     * #run(BeanContainer)}, bounded by {@code com.github.dropguard.summer.shutdown.timeout-ms}.
     */
    public void stop() {
        shutdown(java.time.Duration.ZERO);
    }
}
