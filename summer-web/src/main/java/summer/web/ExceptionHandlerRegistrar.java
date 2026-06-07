package summer.web;

/**
 * Interface for registering exception handlers into an
 * {@link ExceptionRegistry}. Allows switching between reflection-based runtime
 * discovery and static AOT registration.
 */
public interface ExceptionHandlerRegistrar {
	void registerHandlers(ExceptionRegistry registry, summer.core.ApplicationContext context);
}
