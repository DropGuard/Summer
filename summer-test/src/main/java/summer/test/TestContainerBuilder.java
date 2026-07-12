package summer.test;

import java.lang.annotation.Annotation;
import summer.core.BeanContainer;
import summer.core.Component;
import summer.core.DiEngine;

/**
 * Unified test container builder for both AOT and Runtime engines.
 *
 * <pre>{@code
 * // Runtime — full classpath scan
 * TestContainerBuilder.buildRuntime();
 *
 * // Runtime — isolated from seed classes
 * TestContainerBuilder.buildRuntime(A.class, B.class);
 *
 * // AOT — loads pre-generated LocalContext for the test class
 * TestContainerBuilder.buildAot(TestClass.class);
 * }</pre>
 */
public final class TestContainerBuilder {

	private TestContainerBuilder() {
	}

	/**
	 * Builds a {@link BeanContainer} for the Runtime engine.
	 *
	 * <p>
	 * With no arguments, performs a full classpath scan. With seed classes, uses
	 * transitive dependency expansion (isolated).
	 * </p>
	 */
	public static BeanContainer buildRuntime(Class<?>... seeds) {
		return buildRuntimeWithExternal(seeds, new Object[0]);
	}

	public static BeanContainer buildRuntimeWithExternal(Class<?>[] seeds, Object... externalBeans) {
		if (seeds.length > 0) {
			return summer.runtime.RuntimeBeanContainerBuilder.buildFromSeedsWithExternal(seeds, externalBeans);
		}
		return summer.runtime.RuntimeBeanContainerBuilder.build(externalBeans);
	}
	public static BeanContainer buildAot(Class<?> testClass, Object... externalBeans) {
		if (testClass != null) {
			return loadAotLocalContext(testClass, externalBeans);
		}
		try {
			return DiEngine.create(externalBeans);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT context", e);
		}
	}

	private static BeanContainer loadAotLocalContext(Class<?> testClass, Object... externalBeans) {
		String testClassName = testClass.getName().replace('.', '_').replace('$', '_');
		String aotClassName = "summer.core.aot.LocalContext_" + testClassName;
		try {
			Class<?> aotClass = Class.forName(aotClassName);
			return (BeanContainer) aotClass.getMethod("build", Object[].class).invoke(null, (Object) externalBeans);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("AOT LocalContext not found for " + testClass.getName()
					+ ". Ensure summer-maven-plugin is configured for the test phase.", e);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT LocalContext context for " + testClass.getName(), e);
		}
	}
	/**
	 * Checks whether a class has {@code @Component} or a meta-annotation that is
	 * itself annotated with {@code @Component} (e.g. {@code @RestController},
	 * {@code @Configuration}).
	 * 
	 * @param clazz
	 *            the class to check
	 * @return true if the class is a component
	 */
	public static boolean isComponent(Class<?> clazz) {
		if (clazz.isAnnotationPresent(Component.class)) {
			return true;
		}
		for (Annotation ann : clazz.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(Component.class)) {
				return true;
			}
		}
		return false;
	}

	public static BeanContainer buildAot(Class<?> testClass) {
		if (testClass != null) {
			return loadAotLocalContext(testClass);
		}
		try {
			return DiEngine.create();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT context", e);
		}
	}

	private static BeanContainer loadAotLocalContext(Class<?> testClass) {
		String testClassName = testClass.getName().replace('.', '_').replace('$', '_');
		String aotClassName = "summer.core.aot.LocalContext_" + testClassName;
		try {
			Class<?> aotClass = Class.forName(aotClassName);
			return (BeanContainer) aotClass.getMethod("build").invoke(null);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("AOT LocalContext not found for " + testClass.getName()
					+ ". Ensure summer-maven-plugin is configured for the test phase.", e);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create AOT LocalContext context for " + testClass.getName(), e);
		}
	}
}
