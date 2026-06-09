package summer.tck.aop.metadata;

import java.lang.reflect.Method;
import summer.aop.MethodMetadata;
import summer.runtime.RuntimeMethodMetadata;

/**
 * Runtime implementation of the MethodMetadata TCK.
 *
 * <p>
 * Creates {@link RuntimeMethodMetadata} instances by reflecting on
 * {@link MetadataSampleService} methods.
 * </p>
 */
public class RuntimeMethodMetadataTest extends AbstractMethodMetadataTCK {

	@Override
	protected MethodMetadata getMetadataFor(String methodName) {
		for (Method method : MetadataSampleService.class.getDeclaredMethods()) {
			if (method.getName().equals(methodName)) {
				return new RuntimeMethodMetadata(method);
			}
		}
		throw new IllegalArgumentException("No method named " + methodName + " in MetadataSampleService");
	}
}
