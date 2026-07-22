package summer.test.internal;
import java.util.List;
import java.util.Objects;
import summer.core.Internal;

/**
 * The explicit identity of a test universe within the shared
 * {@link SummerTestLifecycle}.
 *
 * <p>
 * Quarkus keys its single shared application on an implicit "environment" it
 * derives from classloader equivalence. Summer has no classloader isolation, so
 * the environment must be modelled explicitly. A {@code @SummerTest} universe
 * is <b>per test class</b> — each class assembles its own graph, and
 * {@code @SummerTest(shouldFail=true)} must always re-attempt its own (failing)
 * assembly rather than reuse another class's container. So the test class
 * identity is itself a key dimension. Within a class, the inputs that change
 * the content of the universe are:
 * <ul>
 * <li><b>profile content</b> — {@code @TestProfile#configOverrides()}
 * values;</li>
 * <li><b>mocked types</b> — the set of {@code @Mock} constructor parameter
 * types.</li>
 * </ul>
 * A separate scope dimension is deliberately <em>absent</em>: the test universe
 * is always full-width, so a scope dimension would offer no distinguishing
 * power (it would be a redundant abstraction). Engine is also absent: Runtime
 * and AOT are two parallel instances keyed by the same EnvKey, not two
 * environments — reuse therefore happens across the two engines of one class,
 * not across classes.
 * </p>
 *
 * <p>
 * The key is built from <em>printable fields</em>, never a black-box hash, so a
 * reuse failure can be answered by reading the key's {@link #toString()}. If
 * two tests unexpectedly share a cached universe, the offending key is logged
 * verbatim rather than as an opaque fingerprint.
 * </p>
 */
@Internal
public final class EnvKey {

	/** Marker used when a test declares no profile. */
	public static final String NO_PROFILE = "<no-profile>";

	private final String profile;
	private final List<String> mockedTypes;
	private final String firstBuilder;

	private EnvKey(String profile, List<String> mockedTypes, String firstBuilder) {
		this.profile = profile;
		this.mockedTypes = List.copyOf(mockedTypes);
		this.firstBuilder = firstBuilder;
	}

	/**
	 * Builds the key for a test class.
	 *
	 * @param profile
	 *            printable content signature of the test's {@code @TestProfile}, or
	 *            {@link #NO_PROFILE}
	 * @param mockedTypes
	 *            fully-qualified names of every type replaced by {@code @Mock}
	 * @param firstBuilder
	 *            the class that first built the cached universe under this key
	 */
	public static EnvKey of(String profile, List<String> mockedTypes, String firstBuilder) {
		return new EnvKey(profile, mockedTypes, firstBuilder);
	}

	public String profile() {
		return profile;
	}

	public List<String> mockedTypes() {
		return mockedTypes;
	}

	public String firstBuilder() {
		return firstBuilder;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof EnvKey other))
			return false;
		// firstBuilder (the test class) IS a key dimension: Summer's universe is
		// per-test-class (each @SummerTest assembles its own graph, and
		// shouldFail depends on the specific class). Without it, distinct classes
		// with the same profile+mocks would share a universe and a
		// @SummerTest(shouldFail=true) would reuse a passing container instead
		// of re-attempting its own (failing) assembly.
		return profile.equals(other.profile) && mockedTypes.equals(other.mockedTypes)
				&& firstBuilder.equals(other.firstBuilder);
	}

	@Override
	public int hashCode() {
		return Objects.hash(profile, mockedTypes, firstBuilder);
	}

	/**
	 * Human-readable, field-explicit rendering — the diagnostic surface for reuse
	 * failures. Never a hash.
	 */
	@Override
	public String toString() {
		return "EnvKey{profile=" + profile + ", mocks=" + mockedTypes + ", builtBy=" + firstBuilder + "}";
	}
}
