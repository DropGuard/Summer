package summer.core.bean;

/**
 * Defines the bounded universe for bean discovery.
 *
 * <p>
 * The pipeline's {@code discover} phase consumes a {@code Scope} at entry;
 * downstream stages ({@code evaluate}, {@code resolve}, {@code materialize})
 * operate on the already-scoped candidate set and remain unaware of scope.
 * </p>
 *
 * <p>
 * Production uses {@link #classpath()}. Integration tests use
 * {@link #packageOf(String)}. Module-scoped tests use
 * {@code testing.scopeFor(testClass)} to derive scope from the test class.
 * </p>
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
	 * Bean discovery narrowed to a package tree. Every {@code @Component} under the
	 * given package is included regardless of dependency relationships.
	 *
	 * @param basePackage
	 *            package prefix (e.g. {@code "com.myapp"})
	 * @return a scope that includes all classes under the package
	 */
	static Scope packageOf(String basePackage) {
		String prefix = basePackage.endsWith(".") ? basePackage : basePackage + ".";
		return name -> name.startsWith(prefix);
	}
}
