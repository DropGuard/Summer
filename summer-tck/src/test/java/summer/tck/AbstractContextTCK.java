package summer.tck;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import summer.core.BeanContainer;
import summer.test.TestContainerBuilder;
import summer.tck.annotation.WithFixtures;

/**
 * Base class for TCK tests that require a {@link BeanContainer}.
 *
 * <p>
 * Provides:
 * <ul>
 * <li>Lazy context creation via {@link #context()}</li>
 * <li>Automatic cleanup in {@link AfterEach}</li>
 * <li>Default {@link #createContext()} using the full merged index (Runtime
 * engine)</li>
 * <li>Support for {@link WithFixtures} annotation for isolated bean
 * registration</li>
 * </ul>
 *
 * <p>
 * Subclasses override {@link #createContext()} to select a different engine:
 *
 * <pre>
 * // AOT — full context
 * protected BeanContainer createContext() {
 * 	return TestContainerBuilder.buildAot();
 * }
 *
 * // AOT — seed-isolated (requires LocalContext pre-generation)
 * protected BeanContainer createContext() {
 * 	return TestContainerBuilder.buildAot(getClass());
 * }
 * </pre>
 *
 * <p>
 * Usage:
 * 
 * <pre>
 * // Simple case: full merged index (no override needed, Runtime engine)
 * public class RuntimeDiTest extends AbstractDependencyInjectionTCK {}
 *
 * // Isolated case: use @WithFixtures for specific fixtures (Runtime)
 * {@literal @}WithFixtures(ConflictConfig.class)
 * public class RuntimeMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {
 * 	// Seeds are automatically expanded via transitive closure
 * }
 * </pre>
 */
public abstract class AbstractContextTCK extends AbstractTCK {

	protected BeanContainer context;

	/**
	 * Create the BeanContainer for testing.
	 *
	 * <p>
	 * Automatically discovers inner {@code @Component} classes of the concrete test
	 * class. If {@link WithFixtures} annotation is present, those classes are added
	 * as additional seeds. Falls back to the full merged index only if no inner
	 * components and no fixtures are found.
	 *
	 * <p>
	 * The DI engine is selected by naming convention: concrete classes whose simple
	 * name starts with {@code Aot} use the AOT engine, other use Runtime.
	 */
	protected BeanContainer createContext() {
		Class<?> testClass = getClass();
		WithFixtures annotation = testClass.getAnnotation(WithFixtures.class);

		// Collect seeds: inner @Component classes + @WithFixtures entries
		List<Class<?>> seeds = new ArrayList<>();
		for (Class<?> inner : testClass.getDeclaredClasses()) {
			if (TestContainerBuilder.isComponent(inner)) {
				seeds.add(inner);
			}
		}
		if (annotation != null) {
			seeds.addAll(List.of(annotation.value()));
		}

		// Default engine is Runtime. AOT tests override this method explicitly.
		if (!seeds.isEmpty()) {
			return TestContainerBuilder.buildRuntime(seeds.toArray(new Class<?>[0]));
		}
		return TestContainerBuilder.buildRuntime();
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
