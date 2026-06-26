package summer.test;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.test.annotation.SummerIntegrationTest;
import summer.test.annotation.SummerTest;

public class SummerExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

	private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(SummerExtension.class);
	private static final String KEY = "BeanContainer";

	@Override
	public void beforeAll(ExtensionContext ctx) throws Exception {
		Class<?> testClass = ctx.getRequiredTestClass();

		Class<?>[] entryBeans = null;
		Engine engine = Engine.RUNTIME;

		SummerTest summerTest = testClass.getAnnotation(SummerTest.class);
		SummerIntegrationTest summerIt = testClass.getAnnotation(SummerIntegrationTest.class);

		if (summerTest != null) {
			entryBeans = summerTest.value();
			engine = summerTest.engine();
		} else if (summerIt != null) {
			entryBeans = summerIt.value();
			engine = summerIt.engine();
		} else {
			return;
		}

		BeanContainer container;
		if (engine == Engine.AOT) {
			container = TestContainerBuilder.buildAot(testClass, entryBeans);
		} else {
			container = TestContainerBuilder.build(entryBeans);
		}

		ctx.getStore(NS).put(KEY, container);
	}

	@Override
	public boolean supportsParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
		return getContext(extCtx) != null && paramCtx.getParameter().getType() == BeanContainer.class;
	}

	@Override
	public Object resolveParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
		BeanContainer container = getContext(extCtx);
		if (container == null)
			throw new ParameterResolutionException("No BeanContainer");
		return container;
	}

	@Override
	public void afterAll(ExtensionContext ctx) throws Exception {
		BeanContainer container = getContext(ctx);
		if (container != null) {
			container.close();
			ctx.getStore(NS).remove(KEY);
		}
	}

	private BeanContainer getContext(ExtensionContext ctx) {
		for (var current = ctx; current != null; current = current.getParent().orElse(null)) {
			BeanContainer c = current.getStore(NS).get(KEY, BeanContainer.class);
			if (c != null)
				return c;
		}
		return null;
	}
}