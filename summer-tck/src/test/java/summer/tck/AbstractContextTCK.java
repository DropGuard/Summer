package summer.tck;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import summer.core.BeanContainer;
import summer.runtime.RuntimeBeanContainerBuilder;
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
	 * Automatically discovers inner {@code @Component} classes of the concrete
	 * test class. If {@link WithFixtures} annotation is present, those classes
	 * are added as additional seeds. Falls back to full classpath scan only if
	 * no inner components and no fixtures are found.
	 */
	protected BeanContainer createContext() {
		Class<?> testClass = getClass();
		WithFixtures annotation = testClass.getAnnotation(WithFixtures.class);

		// Collect seeds: inner @Component classes + @WithFixtures entries
		List<Class<?>> seeds = new ArrayList<>();
		for (Class<?> inner : testClass.getDeclaredClasses()) {
			if (RuntimeBeanContainerBuilder.isComponent(inner)) {
				seeds.add(inner);
			}
		}
		if (annotation != null) {
			seeds.addAll(List.of(annotation.value()));
		}

		if (!seeds.isEmpty()) {
			return RuntimeBeanContainerBuilder.buildFromSeeds(seeds.toArray(new Class<?>[0]));
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
