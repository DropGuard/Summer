package summer.grpc.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ErrorCode;
import summer.core.config.ConfigurationBinder;
import summer.grpc.config.GrpcTlsConfig;
import summer.grpc.exception.SummerGrpcException;

/**
 * Manages HTTP/2 multi-plexed channels (connection pools) for outbound gRPC
 * requests. Maintains a single ManagedChannel per target address.
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@code GrpcInfrastructureConfiguration}.
 * </p>
 */
public class GrpcChannelManager implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(GrpcChannelManager.class);

	private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
	private final GrpcTlsConfig tlsConfig;

	public GrpcChannelManager() {
		this.tlsConfig = ConfigurationBinder.bind(GrpcTlsConfig.class, "grpc.tls");
	}

	/**
	 * Gets or creates a ManagedChannel for the specified target.
	 * 
	 * @param target
	 *            e.g. "localhost:9091"
	 */
	public ManagedChannel getChannel(String target) {
		return channels.computeIfAbsent(target, t -> {
			if (tlsConfig.enabled() && tlsConfig.trustCert() != null) {
				// TLS enabled with CA certificate
				try {
					SslContext sslContext = SslContextBuilder.forClient().trustManager(new File(tlsConfig.trustCert()))
							.build();
					log.info("gRPC TLS enabled for client connection to {}", t);
					return NettyChannelBuilder.forTarget(t).sslContext(sslContext).build();
				} catch (Exception e) {
					log.error("Failed to configure TLS for gRPC client", e);
					throw new SummerGrpcException(ErrorCode.GRPC_ERROR, "Failed to configure TLS", e);
				}
			} else {
				// Plaintext mode (development)
				log.warn("gRPC TLS disabled for client - using plaintext (not recommended for production)");
				return ManagedChannelBuilder.forTarget(t).usePlaintext().build();
			}
		});
	}

	@Override
	public void close() throws Exception {
		for (Map.Entry<String, ManagedChannel> entry : channels.entrySet()) {
			ManagedChannel channel = entry.getValue();
			if (!channel.isShutdown()) {
				try {
					channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
					log.info("Closed gRPC channel to {}", entry.getKey());
				} catch (InterruptedException e) {
					log.error("Error shutting down gRPC channel", e);
					Thread.currentThread().interrupt();
				}
			}
		}
		channels.clear();
	}
}
