package summer.aop;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

/**
 * A zero-reflection {@link MethodMetadata} implementation pre-computed at AOT
 * compile time. Every value is a compile-time constant — no
 * {@code java.lang.reflect.Method} involved.
 *
 * <p>
 * Used by {@code AotProxyGenerator} to emit proxy code that never touches
 * reflection during invocation.
 * </p>
 */
public final class AotMethodMetadata implements MethodMetadata {

	private final String name;
	private final Class<?> declaringClass;
	private final Set<Class<? extends Annotation>> annotations;

	public AotMethodMetadata(String name, Class<?> declaringClass, Set<Class<? extends Annotation>> annotations) {
		this.name = name;
		this.declaringClass = declaringClass;
		this.annotations = Collections.unmodifiableSet(annotations);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Class<?> getDeclaringClass() {
		return declaringClass;
	}

	@Override
	public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
		return annotations.contains(annotationClass);
	}

	@Override
	public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
		return null; // Interceptors only use isAnnotationPresent
	}
}
