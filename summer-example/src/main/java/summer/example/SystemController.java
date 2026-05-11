package summer.example;

import summer.web.annotation.Get;
import summer.web.annotation.RestController;
import summer.web.metrics.MetricsRegistry;
import summer.web.WebContext;

/**
 * System controller for exposing framework-level metrics and health information.
 */
@RestController("/_system")
public class SystemController {

	private final MetricsRegistry registry;

	public SystemController(MetricsRegistry registry) {
		this.registry = registry;
	}

	@Get("/metrics")
	public String metrics(WebContext ctx) {
		// Set content type for Prometheus
		ctx.response().setHeader("Content-Type", "text/plain; version=0.0.4");
		return registry.scrape();
	}
	
	@Get("/health")
	public String health() {
		return "UP";
	}
}
