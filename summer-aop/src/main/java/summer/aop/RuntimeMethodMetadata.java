package summer.aop;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * A runtime implementation of MethodMetadata that wraps a
 * java.lang.reflect.Method. Used exclusively by ProxyFactory for JDK dynamic
 * proxies where reflection is unavoidable.
 */
public class RuntimeMethodMetadata implements MethodMetadata {

	private final Method method;

	public RuntimeMethodMetadata(Method method) {
		this.method = method;
	}

	@Override
	public String getName() {
		return method.getName();
	}

	@Override
	public Class<?> getDeclaringClass() {
		return method.getDeclaringClass();
	}

	@Override
	public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
		return method.isAnnotationPresent(annotationClass);
	}

	@Override
	public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
		return method.getAnnotation(annotationClass);
	}
}
