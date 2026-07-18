package summer.test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.security.MessageDigest;
import java.util.SortedSet;
import java.util.TreeSet;
import summer.core.config.ConfigBinder;
import summer.test.annotation.Mock;

/**
 * Stable identity of an AOT container.
 *
 * <p>
 * An AOT container is generated, compiled, and cached — so two requests that
 * should resolve to the <em>same</em> generated graph must share one identity,
 * and two requests that differ in any input that changes the graph must get
 * distinct identities. This is the AOT-side equivalent of the Runtime engine's
 * {@code Scope}: it is what "test isolation" means on the AOT path.
 * </p>
 *
 * <p>
 * Unlike a hand-picked name, the identity is <b>derived from the inputs that
 * actually determine the container</b> — module boundary, configuration profile
 * content, and the set of mocked types. This mirrors how Quarkus keys its
 * augmentation result on an application fingerprint (classpath + config), so
 * two tests that produce the same application naturally share the result while
 * divergent ones stay isolated, with no manual key to keep in sync.
 * </p>
 *
 * <p>
 * Note on mocked beans: Summer has no standalone "external bean" concept the
 * way the runtime registers pre-instantiated objects — on the test path every
 * replaced bean originates from a {@code @Mock} parameter. So the isolation
 * dimension is the <em>set of mocked types</em>, derived directly from the test
 * constructor's {@code @Mock} annotations, not from a separate external-bean
 * argument. This keeps the identity in lock-step with the actual graph that
 * gets baked into the generated {@code wire()}.
 * </p>
 *
 * <p>
 * Both the cache key and the generated class name are derived from the same
 * fingerprint, so they can never drift apart.
 * </p>
 */
public final class AotKey {

	private final String fingerprint;

	private AotKey(String fingerprint) {
		this.fingerprint = fingerprint;
	}

	/**
	 * Identity for a {@code @SummerTest} container.
	 *
	 * <p>
	 * The universe is always the test universe (application beans plus test beans),
	 * so there is no module/package boundary dimension to encode — Quarkus-aligned,
	 * where {@code @QuarkusTest} sees the whole application plus test beans and
	 * isolation comes from {@code @TestProfile} and {@code @Mock}, not from a
	 * shrunk discovery universe. The only inputs that change the generated graph
	 * are the active profile's <em>content</em> (not its name) and the types
	 * replaced by {@code @Mock}.
	 */
	public static AotKey forTest(Class<?> testClass) {
		SortedSet<String> dimensions = new TreeSet<>();
		dimensions.add("kind=test");
		dimensions.add("test=" + (testClass != null ? testClass.getName() : "<anonymous>"));
		// Profile content (not name) — same overrides ⇒ same container.
		dimensions.add("profile=" + hash(ConfigBinder.getProfileOverrides().toString()));
		// Mocked types — AOT bakes the replacement in at generation time.
		dimensions.add("mocks=" + hash(mockedTypes(testClass)));
		return new AotKey(String.join("|", dimensions));
	}

	/**
	 * Cache key for {@code AotEngine}'s container cache.
	 */
	public String cacheKey() {
		return "aot-" + hash(fingerprint);
	}

	/** Generated class name (without package) — unique per distinct container. */
	public String className() {
		return "GeneratedAot_" + hash(fingerprint);
	}

	@Override
	public String toString() {
		return fingerprint;
	}

	/**
	 * The set of types replaced by {@code @Mock} on the test constructor. Derived
	 * from the annotations, not from instantiated mocks — the identity stage must
	 * not construct anything.
	 */
	private static String mockedTypes(Class<?> testClass) {
		SortedSet<String> names = new TreeSet<>();
		Constructor<?>[] ctors = testClass.getDeclaredConstructors();
		if (ctors.length == 1) {
			Annotation[][] paramAnnotations = ctors[0].getParameterAnnotations();
			Class<?>[] paramTypes = ctors[0].getParameterTypes();
			for (int i = 0; i < paramTypes.length; i++) {
				for (Annotation a : paramAnnotations[i]) {
					if (a instanceof Mock) {
						names.add(paramTypes[i].getName());
						break;
					}
				}
			}
		}
		return String.join(",", names);
	}

	private static String hash(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] bytes = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (Exception e) {
			// Fallback: non-crypto, but deterministic. Should never happen (SHA-256).
			return Integer.toHexString(input.hashCode());
		}
	}
}
