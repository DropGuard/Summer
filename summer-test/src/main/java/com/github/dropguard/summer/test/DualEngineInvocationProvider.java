package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * Runs every {@code @DualEngine} method on BOTH DI engines (Runtime and AOT).
 *
 * <p>A {@code @DualEngine} method lives inside either a {@code @SummerTest}-annotated class or a
 * class with a {@code @RegisterExtension SummerTestExtension} — both paths are detected by {@link
 * #supportsTestTemplate}.
 */
@Internal
public final class DualEngineInvocationProvider implements TestTemplateInvocationContextProvider {

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

    /** One invocation context per engine: builds that engine's container. */
    private static final class EngineContext implements TestTemplateInvocationContext {

        private static final ExtensionContext.Namespace NS =
                ExtensionContext.Namespace.create(DualEngineInvocationProvider.class);
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

        private final class EngineTestInstanceFactory implements TestInstanceFactory {
            @Override
            public Object createTestInstance(
                    TestInstanceFactoryContext factoryContext, ExtensionContext extensionContext)
                    throws TestInstantiationException {
                SummerTestLifecycle.BuildOutcome outcome =
                        SummerTestLifecycle.createUniverse(testClass, engine, extensionContext);
                extensionContext.getStore(NS).put(KEY, outcome.container());
                return outcome.instance();
            }
        }
    }
}
