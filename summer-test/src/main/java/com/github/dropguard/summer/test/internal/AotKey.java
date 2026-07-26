package com.github.dropguard.summer.test.internal;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.test.annotation.Mock;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.security.MessageDigest;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Stable identity of an AOT container.
 *
 * <p>
 * An AOT container is generated, compiled, and cached — so two requests that
 * should resolve to the <em>same</em> generated graph must share one identity,
 * and two requests that differ in any input that changes the graph must get
 * distinct identities. This is the AOT-side equivalent of the Runtime engine's
 * universe scoping: it is what "test isolation" means on the AOT path.
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
@Internal
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
	 *
	 * @param testClass
	 *            the annotated test class
	 * @param overrides
	 *            resolved {@code @TestProfile} content (empty map when none)
	 */
	/**
	 * Identity for a scoped (narrow) {@code @SummerTest(classes=...)} container.
	 * The fingerprint is derived from the seed-class signature, so two scoped tests
	 * with the same closure share a generated graph while different closures stay
	 * isolated — same isolation guarantee as {@link #forTest}, just keyed on the
	 * seed list instead of the whole test universe.
	 *
	 * @param seedSignature
	 *            deterministic signature of the seed classes (sorted names)
	 */
	public static AotKey forNarrow(String seedSignature) {
		return new AotKey("kind=narrow|seeds=" + seedSignature);
	}

	/**
	 * Identity for a whole-universe container built without a test class (e.g.
	 * {@code Testing.build()} from an integration test). Fixed fingerprint — such
	 * containers always observe the same full test universe.
	 */
	public static AotKey forUniverse() {
		return new AotKey("kind=universe");
	}

	public static AotKey forTest(Class<?> testClass, java.util.Map<String, Object> overrides) {
		SortedSet<String> dimensions = new TreeSet<>();
		dimensions.add("kind=test");
		dimensions.add("test=" + (testClass != null ? testClass.getName() : "<anonymous>"));
		// Profile content (not name) — same overrides ⇒ same container. Shared with
		// the AOT wire() generation, so identity and the baked-in BindingContext can
		// never drift apart.
		dimensions.add("profile=" + hash(overrides != null ? overrides.toString() : "{}"));
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
