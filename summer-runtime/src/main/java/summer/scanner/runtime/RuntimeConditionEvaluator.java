package summer.scanner.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import summer.core.annotation.ConditionalOnBean;

final class RuntimeConditionEvaluator {

	private RuntimeConditionEvaluator() {
	}

	static void evaluate(Set<Class<?>> components, Map<Class<?>, Method> beanProducers) {
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Class<?> clazz : new ArrayList<>(components)) {
				ConditionalOnBean cond = clazz.getAnnotation(ConditionalOnBean.class);
				if (cond == null)
					continue;
				Class<?> requiredType = cond.value();
				boolean satisfied = components.stream()
						.anyMatch(c -> requiredType.isAssignableFrom(c) && !c.isInterface());
				if (!satisfied) {
					satisfied = beanProducers.containsKey(requiredType) || beanProducers.entrySet().stream()
							.anyMatch(e -> requiredType.isAssignableFrom(e.getKey()));
				}
				if (!satisfied) {
					components.remove(clazz);
					changed = true;
				}
			}
		}
	}
}
