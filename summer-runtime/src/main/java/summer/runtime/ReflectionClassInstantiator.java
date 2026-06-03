package summer.runtime;

import summer.core.Component;
import summer.core.reflect.ClassInstantiator;

@Component
public class ReflectionClassInstantiator implements ClassInstantiator {

	@Override
	public Object instantiate(String className) throws ReflectiveOperationException {
		Class<?> clazz = Class.forName(className);
		return clazz.getDeclaredConstructor().newInstance();
	}
}
