package summer.grpc.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ApplicationContext;
import summer.core.ApplicationRunner;
import summer.core.Component;
import summer.core.ErrorCode;
import summer.grpc.exception.SummerGrpcException;

@Component
public class GrpcServerRunner implements ApplicationRunner, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(GrpcServerRunner.class);
	private Server server;

	public int getPort() {
		return server != null ? server.getPort() : -1;
	}

	@Override
	public void run(ApplicationContext context) throws Exception {
		List<BindableService> services = context.getBeansOfType(BindableService.class);

		if (services.isEmpty()) {
			return; // No gRPC services to expose
		}

		int port = resolvePort();
		ServerBuilder<?> serverBuilder = ServerBuilder.forPort(port);

		for (BindableService service : services) {
			serverBuilder.addService(service);
			log.info("Route registered (gRPC): {}", service.getClass().getSimpleName());
		}

		this.server = serverBuilder.build();

		try {
			this.server.start();
			log.info("gRPC Server started on port {}", this.server.getPort());

			// Add shutdown hook
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					this.close();
				} catch (Exception e) {
					log.error("Error shutting down gRPC server", e);
				}
			}));
		} catch (IOException e) {
			throw new SummerGrpcException(ErrorCode.GRPC_ERROR, "Failed to start gRPC Server on port " + port, e);
		}
	}

	@Override
	public void close() throws Exception {
		if (server != null && !server.isShutdown()) {
			server.shutdownNow().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
			log.info("gRPC Server stopped");
		}
	}

	private static int resolvePort() {
		String prop = System.getProperty("summer.grpc.port");
		return prop != null ? Integer.parseInt(prop) : 9090;
	}
}
