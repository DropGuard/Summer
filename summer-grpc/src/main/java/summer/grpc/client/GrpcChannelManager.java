package summer.grpc.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.AbstractStub;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.Component;
import summer.core.ErrorCode;
import summer.grpc.exception.SummerGrpcException;

/**
 * Manages HTTP/2 multi-plexed channels (connection pools) for outbound gRPC
 * requests. Maintains a single ManagedChannel per target address.
 */
@Component
public class GrpcChannelManager implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(GrpcChannelManager.class);

	private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

	/**
	 * Gets or creates a ManagedChannel for the specified target.
	 * 
	 * @param target
	 *            e.g. "localhost:9091"
	 */
	public ManagedChannel getChannel(String target) {
		return channels.computeIfAbsent(target, t -> ManagedChannelBuilder.forTarget(t).usePlaintext() // For simplicity
																										// in this
																										// framework,
																										// omit TLS for
																										// now
				.build());
	}

	/**
	 * Helper method to instantiate a BlockingStub using reflection. Example usage
	 * in a @Configuration class: {@code @Bean public UserServiceBlockingStub
	 * userServiceStub() { return manager.getBlockingStub(UserServiceGrpc.class,
	 * "localhost:9091"); } }
	 */
	@SuppressWarnings("unchecked")
	public <T extends AbstractStub<T>> T getBlockingStub(Class<?> grpcClass, String target) {
		ManagedChannel channel = getChannel(target);
		try {
			Method newStubMethod = grpcClass.getMethod("newBlockingStub", io.grpc.Channel.class);
			return (T) newStubMethod.invoke(null, channel);
		} catch (Exception e) {
			throw new SummerGrpcException(ErrorCode.GRPC_ERROR,
					"Failed to create blocking stub for " + grpcClass.getName(), e);
		}
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
