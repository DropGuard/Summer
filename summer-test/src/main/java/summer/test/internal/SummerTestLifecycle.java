package summer.test.internal;

import summer.core.Engine;
import summer.core.Internal;

/**
 * Single owner of the {@code @SummerTest} universe lifecycle.
 *
 * <p>
 * Both the class-level {@link SummerExtension} (single-engine tests) and the
 * method-level {@link DualEngineInvocationProvider} (per-engine template
 * invocations) need a universe for a test class. They must not each
 * re-implement the three-line "resolve engine → build/reuse universe → honour
 * shouldFail" sequence — that duplication is exactly what let the lifecycle
 * drift. This class is the one place that happens; the extensions delegate to
 * it.
 * </p>
 *
 * <p>
 * Universe building, reuse (cache), and the effectiveness report all live in
 * {@link TestRunContext} — this class is the only caller that reaches it, so
 * the cache has a single entry point and the report has a single trigger point.
 * </p>
 */
@Internal
public final class SummerTestLifecycle {

	private SummerTestLifecycle() {
	}

	/**
	 * Resolves (or reuses) the universe for {@code testClass} on {@code engine},
	 * honouring the {@code @SummerTest(shouldFail=...)} contract. Shared by every
	 * Summer test path so negative tests are judged by one rule.
	 */
	public static TestContainerFactory.BuildOutcome createUniverse(Class<?> testClass, Engine engine) {
		return TestContainerFactory.instantiateFor(testClass, engine);
	}
}
