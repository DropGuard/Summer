package summer.test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import summer.core.ApplicationContext;
import summer.core.Engine;
import summer.test.annotation.SummerTest;
import summer.test.annotation.TestProfile;

public class SummerExtension
		implements
			BeforeAllCallback,
			AfterAllCallback,
			TestInstancePostProcessor,
			ParameterResolver {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
			.create(SummerExtension.class);
	private static final String CONTEXT_KEY = "ApplicationContext";

	@Override
	public void beforeAll(ExtensionContext extensionContext) throws Exception {
		Class<?> testClass = extensionContext.getRequiredTestClass();
		SummerTest summerTest = findSummerTest(testClass);
		if (summerTest == null) {
			return;
		}

		SummerTestProfile profile = resolveProfile(testClass);
		ApplicationContext context = createContext(summerTest.engine(), profile);
		extensionContext.getStore(NAMESPACE).put(CONTEXT_KEY, context);
	}

	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext extensionContext) throws Exception {
		ApplicationContext context = getContext(extensionContext);
		if (context != null) {
			injectContext(testInstance, context);
		}
	}

	private SummerTestProfile resolveProfile(Class<?> testClass) {
		TestProfile ann = testClass.getAnnotation(TestProfile.class);
		if (ann == null) {
			return null;
		}
		try {
			return ann.value().getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create SummerTestProfile: " + ann.value().getName(), e);
		}
	}

	private ApplicationContext createContext(Engine engine, SummerTestProfile profile) {
		try {
			return switch (engine) {
				case AOT -> {
					try {
						// AOT: try the new create() factory first, then fall back to old constructor
						Class<?> aotClass = Class.forName("summer.core.aot.GeneratedAotContext");
						try {
							java.lang.reflect.Method createMethod = aotClass.getMethod("create");
							yield (ApplicationContext) createMethod.invoke(null);
						} catch (NoSuchMethodException e) {
							yield (ApplicationContext) aotClass.getConstructor().newInstance();
						}
					} catch (ClassNotFoundException e) {
						throw new IllegalStateException("AOT context not on classpath: " + e.getMessage());
					}
				}
				case RUNTIME -> {
					Class<?> runtimeCtx = Class.forName("summer.runtime.RuntimeApplicationContext");
					java.lang.reflect.Method builderMethod = runtimeCtx.getMethod("builder");
					Object builder = builderMethod.invoke(null);
					Class<?> builderClass = builder.getClass();
					if (profile != null) {
						Set<Class<?>> enabledBeans = profile.getEnabledBeans();
						if (!enabledBeans.isEmpty()) {
							builderClass.getMethod("withEnabledBeans", Set.class).invoke(builder, enabledBeans);
						}
						Map<String, String> configOverrides = profile.getConfigOverrides();
						if (!configOverrides.isEmpty()) {
							builderClass.getMethod("withConfigOverrides", Map.class).invoke(builder, configOverrides);
						}
					}
					yield (ApplicationContext) builderClass.getMethod("build").invoke(builder);
				}
			};
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Engine class not on classpath: " + e.getMessage(), e);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create ApplicationContext", e);
		}
	}

	private void injectContext(Object testInstance, ApplicationContext context) throws IllegalAccessException {
		Field contextField = findContextField(testInstance.getClass());
		if (contextField != null) {
			contextField.setAccessible(true);
			contextField.set(testInstance, context);
		}
	}

	private Field findContextField(Class<?> clazz) {
		Class<?> current = clazz;
		while (current != null) {
			try {
				Field f = current.getDeclaredField("context");
				if (ApplicationContext.class.isAssignableFrom(f.getType())) {
					return f;
				}
			} catch (NoSuchFieldException ignored) {
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private SummerTest findSummerTest(Class<?> clazz) {
		Class<?> current = clazz;
		while (current != null) {
			SummerTest ann = current.getAnnotation(SummerTest.class);
			if (ann != null)
				return ann;
			current = current.getSuperclass();
		}
		return null;
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return getContext(extensionContext) != null
				&& parameterContext.getParameter().getType() == ApplicationContext.class;
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		ApplicationContext context = getContext(extensionContext);
		if (context == null) {
			throw new ParameterResolutionException("No Summer ApplicationContext found in test hierarchy");
		}
		return context;
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
			try {
				context.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			extensionContext.getStore(NAMESPACE).remove(CONTEXT_KEY);
		}
	}
}
