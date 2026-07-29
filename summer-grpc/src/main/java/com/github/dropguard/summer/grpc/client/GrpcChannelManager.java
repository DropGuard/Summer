mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.grpc.client;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.grpc.config.GrpcTlsConfig;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.grpc.exception.SummerGrpcException;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.ManagedChannel;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.ManagedChannelBuilder;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
mport com.github.dropguard.summer.core.Internal;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
mport com.github.dropguard.summer.core.Internal;
import java.io.File;
mport com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
@Internal
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
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
 * Manages HTTP/2 multi-plexed channels (connection pools) for outbound gRPC requests. Maintains a
mport com.github.dropguard.summer.core.Internal;
 * single ManagedChannel per target address.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This is a framework infrastructure bean provided by {@code GrpcInfrastructureConfiguration}.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class GrpcChannelManager implements AutoCloseable {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final Logger log = LoggerFactory.getLogger(GrpcChannelManager.class);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final Map<String, ManagedChannel> channels = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
    private final GrpcTlsConfig tlsConfig;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public GrpcChannelManager(GrpcTlsConfig tlsConfig) {
mport com.github.dropguard.summer.core.Internal;
        this.tlsConfig = tlsConfig;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Gets or creates a ManagedChannel for the specified target.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param target e.g. "localhost:9091"
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public ManagedChannel getChannel(String target) {
mport com.github.dropguard.summer.core.Internal;
        return channels.computeIfAbsent(
mport com.github.dropguard.summer.core.Internal;
                target,
mport com.github.dropguard.summer.core.Internal;
                t -> {
mport com.github.dropguard.summer.core.Internal;
                    if (tlsConfig.enabled() != null
mport com.github.dropguard.summer.core.Internal;
                            && tlsConfig.enabled()
mport com.github.dropguard.summer.core.Internal;
                            && tlsConfig.trustCert() != null) {
mport com.github.dropguard.summer.core.Internal;
                        // TLS enabled with CA certificate
mport com.github.dropguard.summer.core.Internal;
                        try {
mport com.github.dropguard.summer.core.Internal;
                            SslContext sslContext =
mport com.github.dropguard.summer.core.Internal;
                                    SslContextBuilder.forClient()
mport com.github.dropguard.summer.core.Internal;
                                            .trustManager(new File(tlsConfig.trustCert()))
mport com.github.dropguard.summer.core.Internal;
                                            .build();
mport com.github.dropguard.summer.core.Internal;
                            log.info("gRPC TLS enabled for client connection to {}", t);
mport com.github.dropguard.summer.core.Internal;
                            return NettyChannelBuilder.forTarget(t).sslContext(sslContext).build();
mport com.github.dropguard.summer.core.Internal;
                        } catch (Exception e) {
mport com.github.dropguard.summer.core.Internal;
                            log.error("Failed to configure TLS for gRPC client", e);
mport com.github.dropguard.summer.core.Internal;
                            throw new SummerGrpcException("Failed to configure TLS", e);
mport com.github.dropguard.summer.core.Internal;
                        }
mport com.github.dropguard.summer.core.Internal;
                    } else {
mport com.github.dropguard.summer.core.Internal;
                        // Plaintext mode (development)
mport com.github.dropguard.summer.core.Internal;
                        log.warn(
mport com.github.dropguard.summer.core.Internal;
                                "gRPC TLS disabled for client - using plaintext (not recommended"
mport com.github.dropguard.summer.core.Internal;
                                        + " for production)");
mport com.github.dropguard.summer.core.Internal;
                        return ManagedChannelBuilder.forTarget(t).usePlaintext().build();
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                });
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void close() throws Exception {
mport com.github.dropguard.summer.core.Internal;
        for (Map.Entry<String, ManagedChannel> entry : channels.entrySet()) {
mport com.github.dropguard.summer.core.Internal;
            ManagedChannel channel = entry.getValue();
mport com.github.dropguard.summer.core.Internal;
            if (!channel.isShutdown()) {
mport com.github.dropguard.summer.core.Internal;
                try {
mport com.github.dropguard.summer.core.Internal;
                    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
mport com.github.dropguard.summer.core.Internal;
                    log.info("Closed gRPC channel to {}", entry.getKey());
mport com.github.dropguard.summer.core.Internal;
                } catch (InterruptedException e) {
mport com.github.dropguard.summer.core.Internal;
                    log.error("Error shutting down gRPC channel", e);
mport com.github.dropguard.summer.core.Internal;
                    Thread.currentThread().interrupt();
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        channels.clear();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
