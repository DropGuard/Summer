package summer.aop;

import java.lang.annotation.Annotation;

/**
 * Metadata abstraction for intercepted methods. Eliminates the need to use
 * java.lang.reflect.Method in InvocationContexts, allowing AOT compilation to
 * achieve 100% true zero-reflection.
 */
public interface MethodMetadata {

	/**
	 * @return the name of the intercepted method
	 */
	String getName();

	/**
	 * @return the class declaring the intercepted method
	 */
	Class<?> getDeclaringClass();

	/**
	 * Checks if the method has the specified annotation.
	 * 
	 * @param annotationClass
	 *            the annotation class
	 * @return true if present, false otherwise
	 */
	boolean isAnnotationPresent(Class<? extends Annotation> annotationClass);

	/**
	 * Gets the specified annotation if present on the method.
	 * 
	 * @param annotationClass
	 *            the annotation class
	 * @param <T>
	 *            the annotation type
	 * @return the annotation instance or null if not found
	 */
	<T extends Annotation> T getAnnotation(Class<T> annotationClass);
}
