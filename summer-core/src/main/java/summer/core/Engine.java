package summer.core;

/**
 * DI engine selection enum.
 *
 * <p>Controls which dependency injection engine is used at startup:</p>
 * <ul>
 *   <li>{@link #AOT} - Use pre-generated AOT context (fast startup, requires compilation with summer-maven-plugin)</li>
 *   <li>{@link #RUNTIME} - Use runtime classpath scanning (slower startup, no pre-compilation needed)</li>
 * </ul>
 */
public enum Engine {

	/**
	 * Use the AOT-generated context if available, fall back to runtime scanning.
	 */
	AOT,

	/**
	 * Force runtime classpath scanning regardless of AOT availability.
	 */
	RUNTIME
}
