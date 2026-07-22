package summer.test.internal;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.core.bean.MockedBean;
import summer.test.Testing;
import summer.test.annotation.TestProfile;

/**
 * The static, JVM-wide shared test-run context.
 *
 * <p>
 * Mirrors Quarkus' {@code static runningQuarkusApplication}: one application
 * instance is built per environment and reused across every test class, rather
 * than rebuilt per class. Two things live here — both with a lifetime that
 * spans the whole test run, not a single test class:
 * <ul>
 * <li><b>universe cache</b> — {@code @SummerTest} universes keyed by
 * {@link EnvKey}; same key ⇒ same skeleton, reused;</li>
 * <li><b>dev-services holder</b> — the heavy external resources (a real
 * Postgres, …) that a universe may need, started at most once and torn down
 * only at JVM exit.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Because the context is static and shared, the per-class
 * {@code SummerExtension}/{@code DualEngineInvocationProvider} {@code afterAll}
 * callbacks <b>must not</b> close the universe. Closing is the responsibility
 * of this context alone (on environment change or JVM exit), which is what
 * removes the per-class pool leak that motivated this redesign.
 * </p>
 *
 * <p>
 * <b>Escape hatch.</b> Set {@code summer.test.reuseUniverse=false} to fall back
 * to the old per-class rebuild behaviour — the first move when debugging a
 * suspected reuse bug. When reuse is off, this context is a pass-through and
 * every call builds a fresh universe (and leaves its own lifecycle to the
 * caller, exactly as before the redesign).
 * </p>
 */
public final class TestRunContext {

	private static final Logger log = LoggerFactory.getLogger(TestRunContext.class);

	/** System property to disable universe reuse (debugging escape hatch). */
	public static final String REUSE_DISABLED_PROPERTY = "summer.test.reuseUniverse";

	private static final TestRunContext INSTANCE = new TestRunContext();

	/**
	 * EnvKey → cached universe skeleton. Skeletons carry no per-test mock state.
	 *
	 * <p>
	 * JUnit builds test instances serially by default (no parallel configuration in
	 * this project), so {@code acquireUniverse} is only ever called from one thread
	 * at a time — a plain {@link HashMap} is sufficient and avoids the false
	 * impression of concurrency that a {@code ConcurrentHashMap} would imply. The
	 * earlier double-checked locking with an additional {@code synchronized} block
	 * was defending a concurrency model that does not exist here.
	 * </p>
	 */
	private final Map<EnvKey, CachedUniverse> universeCache = new HashMap<>();

	/**
	 * Count of universe-cache hits (reused, not rebuilt). Always incremented on a
	 * cache hit — it backs the framework's own integration-test assertion that the
	 * reuse mechanism actually fires, rather than a user-facing stats switch.
	 * Atomic only so a future parallel mode would stay correct; under the current
	 * serial model a single thread touches it.
	 */
	private final AtomicLong cacheHits = new AtomicLong();

	/** The shared dev-services holder, started lazily and closed on JVM exit. */
	private volatile DevServicesHolder devServices;

	/** Cached "reuse disabled" decision (read once, cheap). */
	private final boolean reuseDisabled = !Boolean.parseBoolean(System.getProperty(REUSE_DISABLED_PROPERTY, "true"));

	private TestRunContext() {
		if (!reuseDisabled) {
			Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "summer-test-run-context-shutdown"));
		}
	}

	/** The single JVM-wide instance. */
	public static TestRunContext instance() {
		return INSTANCE;
	}

	// ── Universe acquisition ──────────────────────────────────────────

	/**
	 * Resolves (or builds) the universe for a {@code @SummerTest} class on the
	 * requested engine.
	 *
	 * <p>
	 * With reuse enabled, universes that share an {@link EnvKey} are built once and
	 * cached; the caller still receives a container appropriate to its engine. With
	 * reuse disabled, this is a straight pass-through to a fresh build.
	 * </p>
	 *
	 * <p>
	 * Concurrency: JUnit creates test instances serially by default (no parallel
	 * configuration in this project), so this method is only ever entered by one
	 * thread at a time. There is no double-checked locking and no concurrent map —
	 * a plain lookup-then-put against a {@link HashMap} is correct and does not
	 * pretend otherwise.
	 * </p>
	 *
	 * @param testClass
	 *            the annotated test class (metadata carrier for profile + mocks)
	 * @param engine
	 *            Runtime or AOT
	 * @param mocks
	 *            mocked beans produced from {@code @Mock} parameters
	 * @return an immutable bean container for injection
	 */
	public BeanContainer acquireUniverse(Class<?> testClass, Engine engine, List<MockedBean> mocks) {
		java.util.Map<String, Object> overrides = profileOverrides(testClass);
		if (reuseDisabled) {
			return Testing.buildForTest(testClass, engine, mocks, overrides);
		}

		EnvKey key = envKeyFor(testClass, mocks);
		CachedUniverse cached = universeCache.get(key);
		if (cached != null) {
			cacheHits.incrementAndGet();
			return cached.container();
		}

		BeanContainer built = Testing.buildForTest(testClass, engine, mocks, overrides);
		universeCache.put(key, new CachedUniverse(built));
		return built;
	}

	/**
	 * Read-only view of the hit count, for the framework's own integration-test
	 * assertion.
	 */
	public long cacheHits() {
		return cacheHits.get();
	}

	/**
	 * Starts the shared dev-services (real database, …) if not already running.
	 * Integration tests call this explicitly before building their universe; the
	 * universe's {@code @Replaces} database config then reads the connection
	 * descriptor from {@link #devServices()}.
	 *
	 * <p>
	 * Deliberately <em>not</em> auto-triggered from universe construction: a wide
	 * {@code @SummerTest} universe always contains a {@code DataSource} (the test's
	 * own H2 {@code @Replaces} swap), so "contains a DataSource" is not a
	 * sufficiently precise signal for "wants a real DB". The integration test knows
	 * its own intent and states it here.
	 * </p>
	 *
	 * @param environment
	 *            hints forwarded to the holder (e.g. requested database name)
	 * @return the connection descriptor, or {@code null} if no holder is available
	 */
	public DevServicesHolder.ConnectionDescriptor ensureDevServices(Map<String, String> environment) {
		DevServicesHolder holder = devServices();
		return holder.start(environment);
	}

	// ── Dev-services ──────────────────────────────────────────────────

	/**
	 * Returns the shared dev-services holder, starting it on first use. The holder
	 * is discovered reflectively (by a well-known class name) so
	 * {@code summer-test} itself has no hard dependency on Testcontainers or
	 * Docker.
	 */
	public DevServicesHolder devServices() {
		DevServicesHolder local = devServices;
		if (local != null) {
			return local;
		}
		synchronized (this) {
			local = devServices;
			if (local != null) {
				return local;
			}
			devServices = local = createHolder();
		}
		return local;
	}

	// ── Lifecycle ─────────────────────────────────────────────────────

	/**
	 * Tears down everything this context owns: cached universes and dev-services.
	 * Safe to call multiple times. Invoked from the shutdown hook and from tests
	 * that need a clean slate.
	 */
	public synchronized void shutdown() {
		for (CachedUniverse c : universeCache.values()) {
			try {
				c.container().close();
			} catch (Exception ignored) {
			}
		}
		universeCache.clear();
		DevServicesHolder local = devServices;
		if (local != null) {
			try {
				local.stop();
			} catch (Exception ignored) {
			}
			devServices = null;
		}
	}

	// ── EnvKey construction ───────────────────────────────────────────

	private EnvKey envKeyFor(Class<?> testClass, List<MockedBean> mocks) {
		String profile = profileSignature(testClass);
		SortedSet<String> mocked = new TreeSet<>();
		for (MockedBean m : mocks) {
			mocked.add(m.targetType().getName());
		}
		return EnvKey.of(profile, List.copyOf(mocked), testClass.getName());
	}

	/**
	 * A cached universe. The map key already guarantees equality, so no extra
	 * key-consistency check is needed here — {@link EnvKey}'s equals/hashCode are
	 * pinned by {@code EnvKeyTest}.
	 */
	private record CachedUniverse(BeanContainer container) {
	}

	private java.util.Map<String, Object> profileOverrides(Class<?> testClass) {
		TestProfile ann = testClass.getAnnotation(TestProfile.class);
		if (ann == null) {
			return java.util.Map.of();
		}
		try {
			summer.test.profile.TestProfileSpec spec = ann.value().getDeclaredConstructor().newInstance();
			return spec.configOverrides();
		} catch (Exception e) {
			return java.util.Map.of();
		}
	}

	private String profileSignature(Class<?> testClass) {
		java.util.Map<String, Object> overrides = profileOverrides(testClass);
		if (overrides.isEmpty()) {
			return EnvKey.NO_PROFILE;
		}
		return overrides.toString();
	}

	@SuppressWarnings("unchecked")
	private DevServicesHolder createHolder() {
		try {
			Class<?> holderClass = Class.forName("summer.test.devservices.TestcontainersDevServicesHolder");
			Constructor<?> ctor = holderClass.getDeclaredConstructor();
			ctor.setAccessible(true);
			return (DevServicesHolder) ctor.newInstance();
		} catch (ClassNotFoundException e) {
			// No holder shipped in this module — integration tests must supply their
			// own DB. Return a no-op so pure-unit modules stay free of Testcontainers.
			return NoOpHolder.INSTANCE;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to instantiate dev-services holder", e);
		}
	}

	/** Placeholder holder used when no real implementation is on the classpath. */
	private static final class NoOpHolder implements DevServicesHolder {
		static final NoOpHolder INSTANCE = new NoOpHolder();

		@Override
		public ConnectionDescriptor start(Map<String, String> requestedEnvironment) {
			return null;
		}

		@Override
		public void stop() {
		}

		@Override
		public boolean owns(String url) {
			return false;
		}

		@Override
		public javax.sql.DataSource sharedDataSource(String url) {
			return null;
		}
	}
}
