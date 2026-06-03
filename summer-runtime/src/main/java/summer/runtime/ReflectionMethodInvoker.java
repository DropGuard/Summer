package summer.runtime;

import java.lang.reflect.Method;
import summer.aop.SummerAopException;
import summer.core.Component;
import summer.core.reflect.MethodInvoker;

@Component
public class ReflectionMethodInvoker implements MethodInvoker {

	@Override
	public Object invokeStatic(Class<?> target, String methodName, Class<?>[] paramTypes, Object... args) {
		try {
			Method method = target.getMethod(methodName, paramTypes);
			return method.invoke(null, args);
		} catch (ReflectiveOperationException e) {
			throw new SummerAopException(
					"Failed to invoke " + target.getName() + "." + methodName, e);
		}
	}
}
