package summer.grpc.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ApplicationRunner;
import summer.core.BeanContainer;
import summer.core.ErrorCode;
import summer.grpc.config.GrpcServerConfig;
import summer.grpc.config.GrpcTlsConfig;
import summer.grpc.exception.SummerGrpcException;

/**
 * gRPC server runner that starts the gRPC server.
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@code GrpcInfrastructureConfiguration}.
 * </p>
 */
public class GrpcServerRunner implements ApplicationRunner, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(GrpcServerRunner.class);

	private final GrpcTlsConfig tlsConfig;
	private final GrpcServerConfig serverConfig;
	private static volatile int actualPort = -1;
	private Server server;

	public GrpcServerRunner(GrpcTlsConfig tlsConfig, GrpcServerConfig serverConfig) {
		this.tlsConfig = tlsConfig;
		this.serverConfig = serverConfig;
	}

	public int getPort() {
		return server != null ? server.getPort() : -1;
	}

	/**
	 * Returns the actual port the gRPC server bound to. Useful when
	 * {@code grpc.server.port} is 0 (random port).
	 */
	public static int getActualPort() {
		return actualPort;
	}

	@Override
	public void run(BeanContainer context) throws Exception {
		List<BindableService> services = context.getBeans(BindableService.class);

		if (services.isEmpty()) {
			return; // No gRPC services to expose
		}

		int port = resolvePort(serverConfig.port());
		ServerBuilder<?> serverBuilder = ServerBuilder.forPort(port);

		List<ServerInterceptor> interceptors = context.getBeans(ServerInterceptor.class);
		for (ServerInterceptor interceptor : interceptors) {
			serverBuilder.intercept(interceptor);
			log.info("gRPC Interceptor registered: {}", interceptor.getClass().getSimpleName());
		}
		// Add GrpcExceptionInterceptor last so it acts as the outermost boundary
		serverBuilder.intercept(new GrpcExceptionInterceptor());

		// Configure TLS if enabled and certificates are provided
		if (tlsConfig.enabled() && tlsConfig.certChain() != null && tlsConfig.privateKey() != null) {
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
			actualPort = this.server.getPort();
			log.info("gRPC Server started on port {}", actualPort);

			// Add shutdown hook
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					this.close();
				} catch (Exception e) {
					log.error("Error shutting down gRPC server", e);
				}
			}));
		} catch (IOException e) {
			throw new SummerGrpcException("Failed to start gRPC Server on port " + port, e);
		}
	}

	@Override
	public void close() throws Exception {
		if (server != null && !server.isShutdown()) {
			server.shutdownNow().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
			log.info("gRPC Server stopped");
		}
	}

	private static int resolvePort(int defaultPort) {
		String prop = System.getProperty("summer.grpc.port");
		return prop != null ? Integer.parseInt(prop) : defaultPort;
	}
}
