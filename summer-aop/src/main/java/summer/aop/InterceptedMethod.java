package summer.aop;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

/**
 * A zero-reflection view of the method currently being intercepted.
 *
 * <p>
 * This is the only method-level information an interceptor receives through
 * {@link InterceptorChain#method()}. It deliberately exposes only what the
 * interception model needs — the method name and whether a given binding
 * annotation is present — and nothing that would require reflection at runtime.
 * </p>
 *
 * <p>
 * Both engines build it without reflection: the Runtime engine derives the name
 * and binding set from the {@code java.lang.reflect.Method} once at proxy
 * creation, while the AOT engine emits the name and a {@code Set} of binding
 * types as compile-time constants. There is no {@code Method} handle and no
 * annotation-instance lookup behind this type, which is exactly why it replaced
 * the old {@code MethodMetadata} abstraction: that abstraction implied a
 * reflection capability neither engine consistently provided.
 * </p>
 *
 * <p>
 * To read an annotation's <i>members</i> (e.g. {@code @CacheEvict("user")} →
 * {@code value()}), reflect on the target method obtained from the invocation
 * context — member access is a business-module concern, out of scope here.
 * </p>
 */
public final class InterceptedMethod {

	private final String name;
	private final Set<Class<? extends Annotation>> annotations;

	public InterceptedMethod(String name, Set<Class<? extends Annotation>> annotations) {
		this.name = name;
		this.annotations = Collections.unmodifiableSet(annotations);
	}

	/**
	 * @return the intercepted method's name
	 */
	public String name() {
		return name;
	}

	/**
	 * Checks if the intercepted method carries the specified annotation. Used for
	 * interceptor binding matching. To read annotation <i>members</i>, obtain the
	 * target method from the invocation context and reflect on it directly.
	 *
	 * @param annotationClass
	 *            the annotation class
	 * @return true if present, false otherwise
	 */
	public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
		return annotations.contains(annotationClass);
	}
}
