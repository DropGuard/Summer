package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import java.util.List;
import java.util.Objects;

/**
 * The explicit identity of a test universe within the shared {@link SummerTestLifecycle}.
 *
 * <p>Quarkus keys its single shared application on an implicit "environment" it derives from
 * classloader equivalence. Summer has no classloader isolation, so the environment must be modelled
 * explicitly. A {@code @SummerTest} universe is <b>per test class</b> — each class assembles its
 * own graph, and {@code @SummerTest(shouldFail=true)} must always re-attempt its own (failing)
 * assembly rather than reuse another class's container. So the test class identity is itself a key
 * dimension. Within a class, the inputs that change the content of the universe are:
 *
 * <ul>
 *   <li><b>profile content</b> — {@code @TestProfile#configOverrides()} values;
 *   <li><b>mocked types</b> — the set of {@code @Mock} constructor parameter types;
 *   <li><b>engine</b> — Runtime and AOT build <em>different</em> containers (different discovery,
 *       different mock handling, generated vs reflective code). Sharing one container across the
 *       two engines would silently test the RUNTIME container twice and never exercise the AOT
 *       engine, so the engine is a mandatory key dimension.
 * </ul>
 *
 * A separate scope dimension is deliberately <em>absent</em>: the test universe is always
 * full-width, so a scope dimension would offer no distinguishing power (it would be a redundant
 * abstraction).
 *
 * <p>The key is built from <em>printable fields</em>, never a black-box hash, so a reuse failure
 * can be answered by reading the key's {@link #toString()}. If two tests unexpectedly share a
 * cached universe, the offending key is logged verbatim rather than as an opaque fingerprint.
 */
@Internal
public final class UniverseKey {

    /** Marker used when a test declares no profile. */
    public static final String NO_PROFILE = "<no-profile>";

    private final String profile;
    private final List<String> mockedTypes;
    private final Engine engine;
    private final String firstBuilder;

    private UniverseKey(
            String profile, List<String> mockedTypes, Engine engine, String firstBuilder) {
        this.profile = profile;
        this.mockedTypes = List.copyOf(mockedTypes);
        this.engine = engine;
        this.firstBuilder = firstBuilder;
    }

    /**
     * Builds the key for a test class.
     *
     * @param profile printable content signature of the test's {@code @TestProfile}, or {@link
     *     #NO_PROFILE}
     * @param mockedTypes fully-qualified names of every type replaced by {@code @Mock}
     * @param engine the DI engine the container is built with (Runtime and AOT never share a cached
     *     universe)
     * @param firstBuilder the class that first built the cached universe under this key
     */
    public static UniverseKey of(
            String profile, List<String> mockedTypes, Engine engine, String firstBuilder) {
        return new UniverseKey(profile, mockedTypes, engine, firstBuilder);
    }

    public String profile() {
        return profile;
    }

    public List<String> mockedTypes() {
        return mockedTypes;
    }

    public Engine engine() {
        return engine;
    }

    public String firstBuilder() {
        return firstBuilder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UniverseKey other)) return false;
        // firstBuilder (the test class) IS a key dimension: Summer's universe is
        // per-test-class (each @SummerTest assembles its own graph, and
        // shouldFail depends on the specific class). Without it, distinct classes
        // with the same profile+mocks would share a universe and a
        // @SummerTest(shouldFail=true) would reuse a passing container instead
        // of re-attempting its own (failing) assembly.
        return profile.equals(other.profile)
                && mockedTypes.equals(other.mockedTypes)
                && engine == other.engine
                && firstBuilder.equals(other.firstBuilder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profile, mockedTypes, engine, firstBuilder);
    }

    /**
     * Human-readable, field-explicit rendering — the diagnostic surface for reuse failures. Never a
     * hash.
     */
    @Override
    public String toString() {
        return "UniverseKey{profile="
                + profile
                + ", mocks="
                + mockedTypes
                + ", engine="
                + engine
                + ", builtBy="
                + firstBuilder
                + "}";
    }
}
