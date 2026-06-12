package summer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import summer.core.config.DefaultValue;
import summer.core.config.DefaultValueResolver;
import summer.core.config.TypeConverter;

public final class RuntimeDefaultValueResolver implements DefaultValueResolver {

	public static final RuntimeDefaultValueResolver INSTANCE = new RuntimeDefaultValueResolver();

	private RuntimeDefaultValueResolver() {
	}

	@Override
	public void applyDefaults(Map<String, Object> section, Class<?> type) {
		if (!type.isRecord())
			return;
		for (RecordComponent component : type.getRecordComponents()) {
			String name = component.getName();
			if (section.containsKey(name))
				continue;
			DefaultValue ann = findAnnotation(component, DefaultValue.class);
			if (ann != null) {
				section.put(name, TypeConverter.convert(ann.value(), component.getType()));
			}
		}
	}

	private static <A extends Annotation> A findAnnotation(RecordComponent component, Class<A> type) {
		for (Annotation a : component.getDeclaredAnnotations()) {
			if (type.isInstance(a))
				return type.cast(a);
		}
		return null;
	}
}
