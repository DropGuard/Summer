package summer.core.bean;

import java.util.Set;
import org.jboss.jandex.IndexView;

/**
 * Defines the bounded universe for bean discovery.
 *
 * <p>
 * The pipeline's {@code discover} phase consumes a {@code Scope} at entry;
 * downstream stages ({@code evaluate}, {@code resolve}, {@code materialize})
 * operate on the already-scoped candidate set and remain unaware of scope.
 * </p>
 *
 * <h3>Dual input channels</h3>
 * <p>
 * The framework accepts beans through two independent channels:
 * <ul>
 *   <li><b>Index</b> — the Jandex index drives BFS discovery and component scanning.</li>
 *   <li><b>Seeds</b> — the caller's explicit declaration of intent.
 *       Seeds are authoritative and always included regardless of index coverage.
 *       {@code BeanDefinitionFactory} resolves unindexed seeds via reflection.</li>
 * </ul>
 * Production uses only the index channel ({@link #classpath()}).
 * Tests use both ({@link #reachableFrom} + explicit seed inclusion).
 * </p>
 *
 * <pre>{@code
 * // Production: full classpath
 * Scope scope = Scope.classpath();
 *
 * // Test isolation: only beans reachable from seeds
 * Scope scope = Scope.reachableFrom(Set.of("com.app.OrderService"), index);
 * }</pre>
 */
@FunctionalInterface
public interface Scope {

	/**
	 * Returns {@code true} if the given class name is within this scope.
	 *
	 * @param className
	 *            fully-qualified class name
	 * @return true if the class is in scope
	 */
	boolean includes(String className);

	/**
	 * Production default: all indexed classes pass.
	 */
	static Scope classpath() {
		return name -> true;
	}

	/**
	 * Test isolation: BFS from seed class names over the Jandex index, computing
	 * the transitive dependency closure.
	 *
	 * <p>
	 * Validates that all seeds are eligible components, then walks constructor
	 * parameters, {@code @Bean} method parameters, and {@code @Replaces} targets
	 * to determine the reachable set.
	 * </p>
	 *
	 * @param seeds
	 *            fully-qualified class names of the entry beans
	 * @param index
	 *            Jandex index for dependency resolution
	 * @return a scope that includes exactly the transitive closure
	 * @throws summer.core.exception.BeanCreationException
	 *             if any seed is invalid
	 */
	static Scope reachableFrom(Set<String> seeds, IndexView index) {
		BeanClosure.validateSeeds(seeds, index);
		Set<String> closure = BeanClosure.compute(seeds, index);
		return closure::contains;
	}
}
