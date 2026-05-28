package summer.core;

/**
 * Interface used to indicate that a bean should run when it is contained within
 * an ApplicationContext. Multiple ApplicationRunner beans can be defined within
 * the same application context and can be ordered using
 * {@link summer.core.Order} (if supported).
 *
 * This is primarily used by engines (like Web Server, gRPC Server, or scheduled
 * tasks) to hook into the startup lifecycle after the context has been fully
 * refreshed.
 */
public interface ApplicationRunner {

	/**
	 * Callback used to run the bean.
	 *
	 * @param context
	 *            the current application context
	 * @throws Exception
	 *             on error
	 */
	void run(ApplicationContext context) throws Exception;
}
