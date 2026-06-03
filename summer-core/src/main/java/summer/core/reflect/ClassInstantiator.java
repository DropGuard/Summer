package summer.core.reflect;

/**
 * Loads a class by name and instantiates it via its no-arg constructor.
 * Implementations are provided by the runtime or AOT engine; consuming modules
 * never call {@code Class.forName} or {@code Constructor.newInstance} directly.
 */
public interface ClassInstantiator {

	/**
	 * Loads the named class and creates a new instance.
	 *
	 * @param className
	 *            the fully qualified class name
	 * @return the new instance
	 * @throws ReflectiveOperationException
	 *             if the class cannot be found or instantiated
	 */
	Object instantiate(String className) throws ReflectiveOperationException;
}
