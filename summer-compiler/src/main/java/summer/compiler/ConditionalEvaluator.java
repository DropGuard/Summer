package summer.compiler;

import java.util.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

final class ConditionalEvaluator {

	private ConditionalEvaluator() {
	}

	static void resolveReplacements(List<BeanDefinition> allBeans, ProcessingEnvironment processingEnv) {
		Map<String, String> replacementMap = new HashMap<>();

		for (BeanDefinition bean : allBeans) {
			if (bean.kind != BeanDefinition.Kind.CONFIGURATION)
				continue;

			TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(bean.qualifiedName);
			if (typeElement == null)
				continue;

			if (!AnnotationHelper.hasAnnotation(typeElement, "summer.core.annotation.Replaces"))
				continue;

			List<TypeMirror> targets = AnnotationHelper.getAnnotationClassListValue(typeElement,
					"summer.core.annotation.Replaces", processingEnv);
			if (targets.isEmpty())
				continue;

			TypeElement targetElement = asTypeElement(targets.get(0), processingEnv);
			if (targetElement == null)
				continue;

			String targetName = targetElement.getQualifiedName().toString();
			if (replacementMap.containsKey(targetName)) {
				processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Duplicate @Replaces: both "
						+ replacementMap.get(targetName) + " and " + bean.qualifiedName + " replace " + targetName,
						typeElement);
				return;
			}

			replacementMap.put(targetName, bean.qualifiedName);
		}

		if (replacementMap.isEmpty())
			return;

		Set<String> replacedNames = replacementMap.keySet();

		allBeans.removeIf(b -> replacedNames.contains(b.qualifiedName) || (b.kind == BeanDefinition.Kind.FACTORY_PRODUCT
				&& b.configClassName != null && replacedNames.contains(b.configClassName)));
	}

	static void evaluateConditions(List<BeanDefinition> allBeans, ProcessingEnvironment processingEnv) {
		Types typeUtils = processingEnv.getTypeUtils();

		boolean changed = true;
		while (changed) {
			changed = false;
			List<BeanDefinition> toRemove = new ArrayList<>();
			for (BeanDefinition bean : allBeans) {
				TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(bean.qualifiedName);
				if (typeElement == null)
					continue;

				javax.lang.model.element.AnnotationMirror condMirror = AnnotationHelper.getAnnotationMirror(typeElement,
						"summer.core.annotation.ConditionalOnBean");
				if (condMirror == null)
					continue;

				Object value = AnnotationHelper.getAnnotationClassValue(condMirror, processingEnv);
				if (!(value instanceof TypeMirror requiredType))
					continue;

				boolean satisfied = false;
				for (BeanDefinition other : allBeans) {
					if (other == bean)
						continue;

					TypeElement otherTypeElement = processingEnv.getElementUtils().getTypeElement(other.qualifiedName);
					if (otherTypeElement == null)
						continue;

					if (typeUtils.isAssignable(typeUtils.erasure(otherTypeElement.asType()),
							typeUtils.erasure(requiredType))) {
						satisfied = true;
						break;
					}
				}
				if (!satisfied) {
					toRemove.add(bean);
				}
			}
			if (!toRemove.isEmpty()) {
				allBeans.removeAll(toRemove);
				changed = true;
			}
		}
	}

	private static TypeElement asTypeElement(TypeMirror typeMirror, ProcessingEnvironment processingEnv) {
		Element element = processingEnv.getTypeUtils().asElement(typeMirror);
		return (element instanceof TypeElement te) ? te : null;
	}
}
