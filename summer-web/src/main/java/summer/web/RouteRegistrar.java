package summer.web;

/**
 * Interface for registering routes and controller adapters. Allows switching
 * between reflection-based runtime discovery and static AOT registration.
 */
public interface RouteRegistrar {
	void registerControllers(HttpRouter.Builder builder, summer.core.ApplicationContext context);
}
