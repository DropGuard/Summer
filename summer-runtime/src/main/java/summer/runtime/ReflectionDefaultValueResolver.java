package summer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.config.DefaultValue;
import summer.core.config.DefaultValueResolver;
import summer.core.config.TypeConverter;

/**
 * Resolves {@link DefaultValue} annotations using Java reflection on record
 * components.
 *
 * <p>
 * This implementation reads annotations directly from
 * {@link RecordComponent#getDeclaredAnnotations()}, which is reliable at
 * runtime but requires the reflection-based DI engine
 * ({@code RuntimeDiMarker}).
 * </p>
 */
public class ReflectionDefaultValueResolver implements DefaultValueResolver {

	private static final Logger log = LoggerFactory.getLogger(ReflectionDefaultValueResolver.class);

	@Override
	public void applyDefaults(Map<String, Object> section, Class<?> type) {
		if (!type.isRecord()) {
			return;
		}

		for (RecordComponent component : type.getRecordComponents()) {
			String name = component.getName();
			if (section.containsKey(name)) {
				continue;
			}

			DefaultValue ann = findAnnotation(component.getDeclaringRecord(), component, DefaultValue.class);
			if (ann != null) {
				Object converted = TypeConverter.convert(ann.value(), component.getType());
				section.put(name, converted);
				log.trace("Applied @DefaultValue(\"{}\") to {}.{}", ann.value(), type.getSimpleName(), name);
			}
		}
	}

	private static <A extends Annotation> A findAnnotation(Class<?> recordType, RecordComponent component,
			Class<A> annotationType) {
		// RecordComponent.getDeclaredAnnotations() is the reliable source
		for (Annotation a : component.getDeclaredAnnotations()) {
			if (annotationType.isInstance(a)) {
				return annotationType.cast(a);
			}
		}
		return null;
	}
}
