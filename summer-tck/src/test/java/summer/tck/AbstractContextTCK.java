package summer.tck;

import org.junit.jupiter.api.AfterEach;
import summer.core.BeanContainer;

/**
 * Base class for TCK tests that require an {@link BeanContainer}.
 *
 * <p>
 * Provides:
 * <ul>
 * <li>Lazy context creation via {@link #context()}</li>
 * <li>Automatic cleanup in {@link AfterEach}</li>
 * <li>Subclasses only need to implement {@link #createContext()}</li>
 * </ul>
 *
 * <p>
 * Usage:
 * 
 * <pre>
 * public abstract class AbstractMyTCK extends AbstractContextTCK {
 * 	// Optional: specify entry components
 * 	&#64;Override
 * 	protected Class&lt;?&gt;[] entryComponents() {
 * 		return new Class&lt;?&gt;[]{MyComponent.class};
 * 	}
 *
 * 	&#64;Test
 * 	void testSomething() {
 * 		MyBean bean = context().getBean(MyBean.class);
 * 		assertNotNull(bean);
 * 	}
 * }
 *
 * // Concrete implementation:
 * public class RuntimeMyTest extends AbstractMyTCK {
 * 	&#64;Override
 * 	protected BeanContainer createContext() {
 * 		var ctx = new RuntimeApplicationContext();
 * 		ctx.scan();
 * 		ctx.initializeBeans();
 * 		return ctx;
 * 	}
 * }
 * </pre>
 */
public abstract class AbstractContextTCK extends AbstractTCK {

	protected BeanContainer context;

	/**
	 * Create the BeanContainer for testing.
	 *
	 * <p>
	 * Implementations typically call:
	 * 
	 * <pre>
	 * var ctx = new RuntimeApplicationContext();
	 * ctx.scan();
	 * ctx.initializeBeans();
	 * return ctx;
	 * </pre>
	 */
	protected abstract BeanContainer createContext();

	/**
	 * Entry components for context creation.
	 *
	 * <p>
	 * Subclasses can override to specify which components to register. Default
	 * returns empty array (scan-based discovery).
	 */
	protected Class<?>[] entryComponents() {
		return new Class<?>[0];
	}

	/**
	 * Get the application context (lazy initialization).
	 */
	protected BeanContainer context() {
		if (context == null) {
			context = createContext();
		}
		return context;
	}

	@AfterEach
	void cleanupContext() {
		closeQuietly(context);
		context = null;
	}
}
