package summer.tck.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies fixture classes for isolated bean registration.
 *
 * <p>
 * Use this annotation on test classes that need isolated bean registration
 * (e.g., intentional conflict scenarios). When present, the test container
 * builds from the exact seed set rather than the full merged index.
 * </p>
 *
 * <pre>{@code
 * @WithFixtures(ConflictConfig.class)
 * public class RuntimeMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {
 * 	// No need to override createContext()
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WithFixtures {
	/**
	 * Fixture classes to register as seed beans.
	 */
	Class<?>[] value();
}
