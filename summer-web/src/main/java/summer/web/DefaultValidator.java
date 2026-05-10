package summer.web;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import summer.validation.ValidationResult;

/**
 * Default validator implementation that supports validation annotations.
 */
public class DefaultValidator implements Validator {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

	@Override
	public ValidationResult validate(Object object) {
		List<String> errors = new ArrayList<>();

		if (object == null) {
			return ValidationResult.invalid("Object is null");
		}

		// Get all fields from the object
		Class<?> clazz = object.getClass();
		for (Field field : clazz.getDeclaredFields()) {
			field.setAccessible(true);

			try {
				Object value = field.get(object);

				// Check @NotNull annotation
				if (field.isAnnotationPresent(NotNull.class)) {
					if (value == null) {
						errors.add(getMessage(field.getAnnotation(NotNull.class), field));
					}
				}

				// Check @NotEmpty annotation (only for strings)
				if (field.isAnnotationPresent(NotEmpty.class)) {
					if (value == null || value.toString().trim().isEmpty()) {
						errors.add(getMessage(field.getAnnotation(NotEmpty.class), field));
					}
				}

				// Check @Email annotation (only for strings)
				if (field.isAnnotationPresent(Email.class)) {
					if (value != null && !EMAIL_PATTERN.matcher(value.toString()).matches()) {
						errors.add(getMessage(field.getAnnotation(Email.class), field));
					}
				}

				// Check @Min annotation (only for numeric types)
				if (field.isAnnotationPresent(Min.class) && value != null) {
					Min annotation = field.getAnnotation(Min.class);
					long minValue = annotation.value();

					if (value instanceof Number) {
						if (((Number) value).longValue() < minValue) {
							errors.add(getMessage(annotation, field).replace("{value}", String.valueOf(minValue)));
						}
					}
				}

				// Check @Max annotation (only for numeric types)
				if (field.isAnnotationPresent(Max.class) && value != null) {
					Max annotation = field.getAnnotation(Max.class);
					long maxValue = annotation.value();

					if (value instanceof Number) {
						if (((Number) value).longValue() > maxValue) {
							errors.add(getMessage(annotation, field).replace("{value}", String.valueOf(maxValue)));
						}
					}
				}

			} catch (IllegalAccessException e) {
				errors.add("Failed to validate field " + field.getName() + ": " + e.getMessage());
			}
		}

		if (errors.isEmpty()) {
			return ValidationResult.valid();
		} else {
			return ValidationResult.invalid(errors);
		}
	}

	private String getMessage(Object annotation, Field field) {
		try {
			java.lang.reflect.Method messageMethod = annotation.getClass().getMethod("message");
			return (String) messageMethod.invoke(annotation);
		} catch (Exception e) {
			return "Validation error for field " + field.getName();
		}
	}

	@Override
	public Class<?> supports() {
		return Object.class; // Supports all types
	}
}