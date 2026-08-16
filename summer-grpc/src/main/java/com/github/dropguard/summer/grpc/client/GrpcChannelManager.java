package com.github.dropguard.summer.grpc.client;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.grpc.config.GrpcClientTlsConfig;
import com.github.dropguard.summer.grpc.exception.SummerGrpcException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages HTTP/2 multi-plexed channels (connection pools) for outbound gRPC requests. Maintains a
 * single ManagedChannel per target address.
 *
 * <p>This is a framework infrastructure bean provided by {@code GrpcInfrastructureConfiguration}.
 *
 * <p>Thread-safety: a single {@link ConcurrentHashMap} keyed by target. {@code computeIfAbsent}
 * guarantees the channel for a given target is built exactly once even when many client threads
 * race for the same target (the mapping function is not re-entrant on this map, so there is no
 * deadlock risk and no duplicate channels). Channel construction (TLS/socket) runs outside any
 * caller-visible lock, so a slow connect cannot stall other callers. This is an explicit exception
 * to the project's usual ConcurrentHashMap ban — see {@code ArchitectureTest#noConcurrentHashMap}.
 */
@Internal
public class GrpcChannelManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GrpcChannelManager.class);

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final GrpcClientTlsConfig tlsConfig;

    public GrpcChannelManager(GrpcClientTlsConfig tlsConfig) {
        this.tlsConfig = tlsConfig;
    }

    /**
     * Gets or creates a ManagedChannel for the specified target.
     *
     * <p>Concurrent, multi-threaded access is safe. A target already being built by another thread
     * shares the same channel — callers block briefly on the in-flight computation rather than each
     * building their own (which would leak connections). If channel construction fails, the broken
     * entry is not retained, so a later call can retry.
     *
     * @param target e.g. "localhost:9091"
     */
    public ManagedChannel getChannel(String target) {
        return channels.computeIfAbsent(target, this::createChannel);
    }

    private ManagedChannel createChannel(String target) {
        if (tlsConfig.enabled() != null && tlsConfig.enabled()) {
            // TLS was requested. GrpcClientTlsValidator rejects enabled-without-trustCert at
            // binding
            // time; this guard is a second line of defence so a client can never silently send
            // plaintext when TLS was requested.
            if (tlsConfig.trustCert() == null) {
                throw new SummerGrpcException(
                        io.grpc.Status.FAILED_PRECONDITION.withDescription(
                                "grpc.client.tls.enabled is true but trust-cert is missing —"
                                        + " refusing to connect in plaintext."));
            }
            try {
                SslContext sslContext =
                        SslContextBuilder.forClient()
                                .trustManager(new File(tlsConfig.trustCert()))
                                .build();
                log.info("gRPC TLS enabled for client connection to {}", target);
                return NettyChannelBuilder.forTarget(target).sslContext(sslContext).build();
            } catch (Exception e) {
                log.error("Failed to configure TLS for gRPC client", e);
                throw new SummerGrpcException(
                        io.grpc.Status.INTERNAL.withDescription("Failed to configure TLS"), e);
            }
        } else {
            // Plaintext mode (development)
            log.warn(
                    "gRPC TLS disabled for client - using plaintext (not recommended for"
                            + " production)");
            return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        }
    }

    @Override
    public void close() throws Exception {
        List<ManagedChannel> toClose = List.copyOf(channels.values());
        channels.clear();
        for (ManagedChannel channel : toClose) {
            if (!channel.isShutdown()) {
                try {
                    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                    log.info("Closed gRPC channel to {}", channel.authority());
                } catch (InterruptedException e) {
                    log.error("Error shutting down gRPC channel", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
