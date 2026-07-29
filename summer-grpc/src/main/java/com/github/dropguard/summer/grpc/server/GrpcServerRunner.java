mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.grpc.server;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.ApplicationRunner;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.BeanContainer;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.grpc.config.GrpcServerConfig;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.grpc.config.GrpcTlsConfig;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.grpc.exception.SummerGrpcException;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.BindableService;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.Server;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.ServerBuilder;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.ServerInterceptor;
mport com.github.dropguard.summer.core.Internal;
@Internal
import java.io.File;
mport com.github.dropguard.summer.core.Internal;
import java.io.IOException;
mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;
import java.util.concurrent.TimeUnit;
mport com.github.dropguard.summer.core.Internal;
import org.slf4j.Logger;
mport com.github.dropguard.summer.core.Internal;
import org.slf4j.LoggerFactory;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * gRPC server runner that starts the gRPC server.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This is a framework infrastructure bean provided by {@code GrpcInfrastructureConfiguration}.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class GrpcServerRunner implements ApplicationRunner {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final Logger log = LoggerFactory.getLogger(GrpcServerRunner.class);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final GrpcTlsConfig tlsConfig;
mport com.github.dropguard.summer.core.Internal;
    private final GrpcServerConfig serverConfig;
mport com.github.dropguard.summer.core.Internal;
    private Server server;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public GrpcServerRunner(GrpcTlsConfig tlsConfig, GrpcServerConfig serverConfig) {
mport com.github.dropguard.summer.core.Internal;
        this.tlsConfig = tlsConfig;
mport com.github.dropguard.summer.core.Internal;
        this.serverConfig = serverConfig;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public int getPort() {
mport com.github.dropguard.summer.core.Internal;
        return server != null ? server.getPort() : -1;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void run(BeanContainer context) throws Exception {
mport com.github.dropguard.summer.core.Internal;
        List<BindableService> services = context.getBeans(BindableService.class);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        if (services.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            return; // No gRPC services to expose
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        int port = resolvePort(serverConfig.port());
mport com.github.dropguard.summer.core.Internal;
        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(port);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        List<ServerInterceptor> interceptors = context.getBeans(ServerInterceptor.class);
mport com.github.dropguard.summer.core.Internal;
        for (ServerInterceptor interceptor : interceptors) {
mport com.github.dropguard.summer.core.Internal;
            serverBuilder.intercept(interceptor);
mport com.github.dropguard.summer.core.Internal;
            log.info("gRPC Interceptor registered: {}", interceptor.getClass().getSimpleName());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        // Add GrpcExceptionInterceptor last so it acts as the outermost boundary
mport com.github.dropguard.summer.core.Internal;
        serverBuilder.intercept(new GrpcExceptionInterceptor());
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        // Configure TLS if enabled and certificates are provided
mport com.github.dropguard.summer.core.Internal;
        if (tlsConfig.enabled()
mport com.github.dropguard.summer.core.Internal;
                && tlsConfig.certChain() != null
mport com.github.dropguard.summer.core.Internal;
                && tlsConfig.privateKey() != null) {
mport com.github.dropguard.summer.core.Internal;
            File certChainFile = new File(tlsConfig.certChain());
mport com.github.dropguard.summer.core.Internal;
            File privateKeyFile = new File(tlsConfig.privateKey());
mport com.github.dropguard.summer.core.Internal;
            serverBuilder.useTransportSecurity(certChainFile, privateKeyFile);
mport com.github.dropguard.summer.core.Internal;
            log.info("gRPC TLS enabled with cert: {}", tlsConfig.certChain());
mport com.github.dropguard.summer.core.Internal;
        } else {
mport com.github.dropguard.summer.core.Internal;
            log.warn("gRPC TLS disabled - using plaintext (not recommended for production)");
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        for (BindableService service : services) {
mport com.github.dropguard.summer.core.Internal;
            serverBuilder.addService(service);
mport com.github.dropguard.summer.core.Internal;
            log.info("Route registered (gRPC): {}", service.getClass().getSimpleName());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        this.server = serverBuilder.build();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            this.server.start();
mport com.github.dropguard.summer.core.Internal;
            log.info("gRPC Server started on port {}", this.server.getPort());
mport com.github.dropguard.summer.core.Internal;
        } catch (IOException e) {
mport com.github.dropguard.summer.core.Internal;
            throw new SummerGrpcException("Failed to start gRPC Server on port " + port, e);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        long timeoutMs = context.getShutdownConfig().timeoutMs();
mport com.github.dropguard.summer.core.Internal;
        context.addShutdownTask(() -> shutdown(java.time.Duration.ofMillis(timeoutMs)));
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void shutdown(java.time.Duration timeout) {
mport com.github.dropguard.summer.core.Internal;
        if (server == null || server.isShutdown()) {
mport com.github.dropguard.summer.core.Internal;
            return;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        log.info("Shutting down gRPC Server...");
mport com.github.dropguard.summer.core.Internal;
        // gRPC initiates graceful shutdown: rejects new calls, lets in-flight
mport com.github.dropguard.summer.core.Internal;
        // ones finish, then terminates.
mport com.github.dropguard.summer.core.Internal;
        server.shutdown();
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            if (!timeout.isZero()) {
mport com.github.dropguard.summer.core.Internal;
                server.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            server.awaitTermination();
mport com.github.dropguard.summer.core.Internal;
        } catch (InterruptedException e) {
mport com.github.dropguard.summer.core.Internal;
            Thread.currentThread().interrupt();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        log.info("gRPC Server stopped");
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Convenience for direct/test use: stops the server immediately (zero drain timeout). The
mport com.github.dropguard.summer.core.Internal;
     * container drives the same staging via the shutdown task registered in {@link
mport com.github.dropguard.summer.core.Internal;
     * #run(BeanContainer)}, bounded by {@code com.github.dropguard.summer.shutdown.timeout-ms}.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void stop() {
mport com.github.dropguard.summer.core.Internal;
        shutdown(java.time.Duration.ZERO);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private int resolvePort(int defaultPort) {
mport com.github.dropguard.summer.core.Internal;
        String prop = System.getProperty("com.github.dropguard.summer.grpc.port");
mport com.github.dropguard.summer.core.Internal;
        return prop != null ? Integer.parseInt(prop) : defaultPort;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
