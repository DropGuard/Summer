package summer.test.profile;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.test.TestContainerFactory;
import summer.test.annotation.SummerTest;

/**
 * Runs every {@code @SummerTest} method on BOTH DI engines (Runtime and AOT),
 * transparently to the test author — the framework-enforced guarantee that the
 * two engines behave identically.
 *
 * <p>
 * Registered by the method-level {@code @DualEngine} annotation (which combines
 * {@code @TestTemplate} with
 * {@code @ExtendWith(DualEngineInvocationProvider.class)}). For each
 * {@code @DualEngine} method the provider yields two
 * {@link TestTemplateInvocationContext}s, one per engine. Each context builds
 * its own container through {@link TestContainerFactory} (the same factory
 * {@code SummerExtension} uses for single-engine tests) and injects the test
 * constructor from it, so constructor injection, {@code @Mock} handling, and
 * {@code @TestProfile} overrides are engine-agnostic.
 * </p>
 *
 * <p>
 * Both engines receive the identical scope and profile overrides, so a
 * divergence in behavior surfaces as a per-engine failure in the test report
 * ({@code MyTest(RUNTIME)} vs {@code MyTest(AOT)}) rather than a silent
 * inconsistency.
 * </p>
 */
public final class DualEngineInvocationProvider implements TestTemplateInvocationContextProvider {

	@Override
	public boolean supportsTestTemplate(ExtensionContext context) {
		// A @DualEngine method lives inside a @SummerTest-annotated class, which
		// owns the scope/isolation metadata. The class is what carries @SummerTest.
		return context.getTestClass().map(c -> c.isAnnotationPresent(SummerTest.class)).orElse(false);
	}

	@Override
	public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
		Class<?> testClass = context.getRequiredTestClass();
		return Stream.of(Engine.RUNTIME, Engine.AOT).map(engine -> new EngineContext(engine, testClass));
	}

	/** One invocation context per engine: builds that engine's container. */
	private static final class EngineContext implements TestTemplateInvocationContext {

		private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace
				.create(DualEngineInvocationProvider.class);
		private static final String KEY = "BeanContainer";

		private final Engine engine;
		private final Class<?> testClass;

		EngineContext(Engine engine, Class<?> testClass) {
			this.engine = engine;
			this.testClass = testClass;
		}

		@Override
		public String getDisplayName(int invocationIndex) {
			return "(" + engine + ")";
		}

		@Override
		public List<Extension> getAdditionalExtensions() {
			return List.of(new EngineTestInstanceFactory());
		}

		/** Builds the engine's container and resolves the test constructor. */
		private final class EngineTestInstanceFactory implements TestInstanceFactory, AfterAllCallback {

			@Override
			public Object createTestInstance(TestInstanceFactoryContext factoryContext,
					ExtensionContext extensionContext) throws TestInstantiationException {
				BeanContainer container = TestContainerFactory.build(testClass, engine);
				extensionContext.getStore(NS).put(KEY, container);
				return TestContainerFactory.instantiate(testClass, container);
			}

			@Override
			public void afterAll(ExtensionContext ctx) throws Exception {
				BeanContainer container = ctx.getStore(NS).get(KEY, BeanContainer.class);
				if (container != null) {
					try {
						container.close();
					} catch (Exception ignored) {
					}
					ctx.getStore(NS).remove(KEY);
				}
			}
		}
	}
}
