package summer.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;

/**
 * Stateless utility methods for reading annotation values from
 * {@link Element}s. All methods are static — they receive
 * {@link ProcessingEnvironment} as a parameter.
 */
final class AnnotationHelper {

	private static final String VALUE = "value";

	private AnnotationHelper() {
	}

	static boolean hasAnnotation(Element element, String annotationFqn) {
		for (AnnotationMirror am : element.getAnnotationMirrors()) {
			if (am.getAnnotationType().toString().equals(annotationFqn)) {
				return true;
			}
		}
		return false;
	}

	static AnnotationMirror getAnnotationMirror(Element element, String annotationFqn) {
		for (AnnotationMirror am : element.getAnnotationMirrors()) {
			if (am.getAnnotationType().toString().equals(annotationFqn)) {
				return am;
			}
		}
		return null;
	}

	static String getAnnotationStringValue(Element element, String annotationFqn, ProcessingEnvironment processingEnv) {
		for (AnnotationMirror am : element.getAnnotationMirrors()) {
			if (am.getAnnotationType().toString().equals(annotationFqn)) {
				Map<? extends ExecutableElement, ? extends AnnotationValue> values = processingEnv.getElementUtils()
						.getElementValuesWithDefaults(am);
				for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
					if (entry.getKey().getSimpleName().toString().equals(VALUE)) {
						return entry.getValue().getValue().toString();
					}
				}
			}
		}
		return "";
	}

	static Object getAnnotationClassValue(AnnotationMirror mirror, ProcessingEnvironment processingEnv) {
		for (var entry : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
			if (entry.getKey().getSimpleName().toString().equals(VALUE)) {
				return entry.getValue().getValue();
			}
		}
		return null;
	}

	static List<TypeMirror> getAnnotationClassListValue(Element element, String annotationFqn,
			ProcessingEnvironment processingEnv) {
		List<TypeMirror> result = new ArrayList<>();
		for (AnnotationMirror am : element.getAnnotationMirrors()) {
			if (am.getAnnotationType().toString().equals(annotationFqn)) {
				Map<? extends ExecutableElement, ? extends AnnotationValue> values = processingEnv.getElementUtils()
						.getElementValuesWithDefaults(am);
				for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
					if (entry.getKey().getSimpleName().toString().equals(VALUE)) {
						extractTypeMirrors(entry.getValue().getValue(), result);
					}
				}
			}
		}
		return result;
	}

	private static void extractTypeMirrors(Object val, List<TypeMirror> result) {
		if (val instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof AnnotationValue av) {
					Object innerVal = av.getValue();
					if (innerVal instanceof TypeMirror tm) {
						result.add(tm);
					}
				}
			}
		} else if (val instanceof TypeMirror tm) {
			result.add(tm);
		}
	}

	static List<String> getAnnotationStringArrayValue(AnnotationMirror am, String paramName,
			ProcessingEnvironment processingEnv) {
		List<String> result = new ArrayList<>();
		Map<? extends ExecutableElement, ? extends AnnotationValue> values = processingEnv.getElementUtils()
				.getElementValuesWithDefaults(am);
		for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
			if (entry.getKey().getSimpleName().toString().equals(paramName)) {
				extractStrings(entry.getValue().getValue(), result);
			}
		}
		return result;
	}

	private static void extractStrings(Object val, List<String> result) {
		if (val instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof AnnotationValue av) {
					result.add(av.getValue().toString().replace("\"", ""));
				}
			}
		} else if (val instanceof String s) {
			result.add(s);
		}
	}
}
