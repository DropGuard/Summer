package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * Runs every {@code @DualEngine} method on BOTH DI engines (Runtime and AOT).
 *
 * <p>A {@code @DualEngine} method lives inside either a {@code @SummerTest}-annotated class or a
 * class with a {@code @RegisterExtension SummerTestExtension} — both paths are detected by {@link
 * #supportsTestTemplate}.
 *
 * <p>JUnit's {@code TestTemplate} invocations share a single test instance (created by the
 * class-level {@code SummerExtension}, which hardcodes {@code Engine.RUNTIME}) — a per-invocation
 * {@code TestInstanceFactory} is never consulted. So the AOT container cannot be injected into the
 * test instance; instead this provider builds each engine's container in {@code beforeEach} (a real
 * AOT compile+load for the AOT invocation — a build failure fails the test), and exposes that
 * container to the test method via a {@code BeanContainer} parameter (see {@link
 * #isEngineInvocation}). Tests that must assert AOT behaviour declare {@code BeanContainer} as a
 * method parameter and read beans from it.
 */
@Internal
public final class DualEngineInvocationProvider implements TestTemplateInvocationContextProvider {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(DualEngineInvocationProvider.class);
    private static final String KEY = "container";

    /**
     * Whether the given context is inside a {@code @DualEngine} test-method invocation. The {@code
     * SummerExtension} parameter resolver uses this to decline parameters there — exactly one
     * resolver claims a {@code BeanContainer} parameter in either world: this provider's
     * invocation-scoped resolver during a {@code @DualEngine} invocation, {@code SummerExtension}'s
     * otherwise.
     */
    static boolean isEngineInvocation(ExtensionContext ec) {
        return ec.getStore(NS).get(KEY) != null;
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestClass()
                .map(
                        c ->
                                c.isAnnotationPresent(SummerTest.class)
                                        || SummerTestExtension.resolve(c) != null)
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        return Stream.of(Engine.RUNTIME, Engine.AOT)
                .map(engine -> new EngineContext(engine, testClass));
    }

    /** One invocation context per engine: builds that engine's container before the method runs. */
    private static final class EngineContext implements TestTemplateInvocationContext {

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
            return List.of(new EngineContainerBuilder(), new EngineParameterResolver());
        }

        /**
         * Builds this invocation's container before the test method executes. The RUNTIME
         * invocation hits the {@code SummerTestLifecycle} cache (the instance was already built by
         * {@code SummerExtension}); the AOT invocation performs a real compile+load — the only
         * place the AOT engine is actually exercised by the suite.
         *
         * <p>Runs as a {@link BeforeEachCallback} (not beforeTestExecution) so this invocation's
         * container is already bound when user {@code @BeforeEach} methods resolve their {@code
         * BeanContainer} parameters.
         */
        private final class EngineContainerBuilder implements BeforeEachCallback {
            @Override
            public void beforeEach(ExtensionContext context) {
                SummerTestLifecycle.BuildOutcome outcome =
                        SummerTestLifecycle.createUniverse(testClass, engine);
                context.getStore(NS).put(KEY, outcome.container());
            }
        }

        /**
         * Resolves a {@code BeanContainer} parameter from THIS invocation's engine container,
         * letting a {@code @DualEngine} method assert behaviour against the engine that built it
         * (the test instance itself always comes from the RUNTIME container). Applicable only
         * during an invocation — {@code SummerExtension} claims the same type everywhere else, so
         * the two resolvers never compete for one parameter.
         */
        private final class EngineParameterResolver implements ParameterResolver {
            @Override
            public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
                return pc.getParameter().getType() == BeanContainer.class;
            }

            @Override
            public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
                BeanContainer container = (BeanContainer) ec.getStore(NS).get(KEY);
                if (container == null) {
                    throw new IllegalStateException(
                            "No container for @DualEngine invocation (engine=" + engine + ")");
                }
                return container;
            }
        }
    }
}
