package summer.tck;

import org.junit.jupiter.api.AfterEach;
import summer.core.BeanContainer;
import summer.test.TestContainerBuilder;
import summer.test.annotation.WithFixtures;

/**
 * Base class for TCK tests that require a {@link BeanContainer}.
 *
 * <p>
 * Provides:
 * <ul>
 * <li>Lazy context creation via {@link #context()}</li>
 * <li>Automatic cleanup in {@link AfterEach}</li>
 * <li>Default {@link #createContext()} using full classpath scan</li>
 * <li>Support for {@link WithFixtures} annotation for isolated bean
 * registration</li>
 * </ul>
 *
 * <p>
 * Usage:
 * 
 * <pre>
 * // Simple case: full classpath scan (no override needed)
 * public class RuntimeDiTest extends AbstractDependencyInjectionTCK {
 * 	// Uses default createContext()
 * }
 *
 * // Isolated case: use @WithFixtures for specific fixtures
 * {@literal @}WithFixtures(ConflictConfig.class)
 * public class RuntimeMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {
 * 	// No need to override createContext()
 * }
 * </pre>
 */
public abstract class AbstractContextTCK extends AbstractTCK {

	protected BeanContainer context;

	/**
	 * Create the BeanContainer for testing.
	 *
	 * <p>
	 * Default: full classpath scan via {@code TestContainerBuilder}. If
	 * {@link WithFixtures} annotation is present, uses entry beans for isolation.
	 */
	protected BeanContainer createContext() {
		WithFixtures annotation = getClass().getAnnotation(WithFixtures.class);
		if (annotation != null) {
			return TestContainerBuilder.create().withEntryBeans(annotation.value()).build();
		}
		return TestContainerBuilder.create().build();
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
