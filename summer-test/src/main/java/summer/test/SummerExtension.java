package summer.test;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import summer.core.ApplicationContext;
import summer.core.DiEngine;
import summer.test.annotation.SummerTest;

/**
 * JUnit 5 Extension that handles the lifecycle of the Summer ApplicationContext
 * and automatically injects beans into test constructors or methods.
 */
public class SummerExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
			.create(SummerExtension.class);
	private static final String CONTEXT_KEY = "ApplicationContext";

	@Override
	public void beforeAll(ExtensionContext extensionContext) throws Exception {
		Class<?> testClass = extensionContext.getRequiredTestClass();
		SummerTest summerTest = testClass.getAnnotation(SummerTest.class);

		if (summerTest != null) {
			DiEngine engine = summerTest.value().getConstructor().newInstance();
			ApplicationContext context = engine.create(testClass);
			extensionContext.getStore(NAMESPACE).put(CONTEXT_KEY, context);
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return getContext(extensionContext) != null;
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		ApplicationContext context = getContext(extensionContext);
		Class<?> paramType = parameterContext.getParameter().getType();

		try {
			return context.getBean(paramType);
		} catch (Exception e) {
			throw new ParameterResolutionException("Could not resolve bean of type " + paramType.getName(), e);
		}
	}

	private ApplicationContext getContext(ExtensionContext extensionContext) {
		ExtensionContext current = extensionContext;
		while (current != null) {
			ApplicationContext context = current.getStore(NAMESPACE).get(CONTEXT_KEY, ApplicationContext.class);
			if (context != null) {
				return context;
			}
			current = current.getParent().orElse(null);
		}
		return null;
	}

	@Override
	public void afterAll(ExtensionContext extensionContext) throws Exception {
		ApplicationContext context = extensionContext.getStore(NAMESPACE).get(CONTEXT_KEY, ApplicationContext.class);
		if (context != null) {
			context.destroy();
			extensionContext.getStore(NAMESPACE).remove(CONTEXT_KEY);
		}
	}
}
