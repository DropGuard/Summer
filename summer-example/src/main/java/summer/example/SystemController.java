package summer.example;

import summer.web.HttpStatus;
import summer.web.WebContext;
import summer.web.annotation.Get;
import summer.web.annotation.RestController;
import summer.web.metrics.MetricsRegistry;

/**
 * System controller for exposing framework-level metrics and health
 * information.
 */
@RestController("/_system")
public class SystemController {

	private final MetricsRegistry registry;

	public SystemController(MetricsRegistry registry) {
		this.registry = registry;
	}

	@Get("/metrics")
	public void metrics(WebContext ctx) {
		ctx.setHeader("Content-Type", "text/plain; version=0.0.4");
		ctx.text(HttpStatus.OK, registry.scrape());
	}

	@Get("/health")
	public void health(WebContext ctx) {
		ctx.text(HttpStatus.OK, "UP");
	}
}
