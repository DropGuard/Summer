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
		Map<TypeElement, TypeElement> replacementMap = new HashMap<>();

		for (BeanDefinition bean : allBeans) {
			if (bean.kind() != BeanDefinition.Kind.CONFIGURATION)
				continue;
			if (!(bean instanceof AptBeanDefinition aptBean))
				continue;
			if (!AnnotationHelper.hasAnnotation(aptBean.typeElement, "summer.core.annotation.Replaces"))
				continue;

			List<TypeMirror> targets = AnnotationHelper.getAnnotationClassListValue(aptBean.typeElement,
					"summer.core.annotation.Replaces", processingEnv);
			if (targets.isEmpty())
				continue;

			TypeElement targetElement = asTypeElement(targets.get(0), processingEnv);
			if (targetElement == null)
				continue;

			if (replacementMap.containsKey(targetElement)) {
				processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
						"Duplicate @Replaces: both " + replacementMap.get(targetElement).getQualifiedName() + " and "
								+ aptBean.typeElement.getQualifiedName() + " replace "
								+ targetElement.getQualifiedName(),
						aptBean.typeElement);
				return;
			}

			replacementMap.put(targetElement, aptBean.typeElement);
		}

		if (replacementMap.isEmpty())
			return;

		Set<String> replacedNames = new HashSet<>();
		for (TypeElement replaced : replacementMap.keySet()) {
			replacedNames.add(replaced.getQualifiedName().toString());
		}

		allBeans.removeIf(bean -> replacedNames.contains(bean.qualifiedName())
				|| (bean.kind() == BeanDefinition.Kind.FACTORY_PRODUCT && bean.configClassName() != null
						&& replacedNames.contains(bean.configClassName())));
	}

	static void evaluateConditions(List<BeanDefinition> allBeans, ProcessingEnvironment processingEnv) {
		Types typeUtils = processingEnv.getTypeUtils();

		boolean changed = true;
		while (changed) {
			changed = false;
			List<BeanDefinition> toRemove = new ArrayList<>();
			for (BeanDefinition bean : allBeans) {
				if (!(bean instanceof AptBeanDefinition aptBean))
					continue;

				javax.lang.model.element.AnnotationMirror condMirror = AnnotationHelper
						.getAnnotationMirror(aptBean.typeElement, "summer.core.annotation.ConditionalOnBean");
				if (condMirror == null)
					continue;

				Object value = AnnotationHelper.getAnnotationClassValue(condMirror, processingEnv);
				if (!(value instanceof TypeMirror requiredType))
					continue;

				boolean satisfied = false;
				for (BeanDefinition other : allBeans) {
					if (other == bean)
						continue;
					if (other instanceof AptBeanDefinition otherApt) {
						if (typeUtils.isAssignable(typeUtils.erasure(otherApt.typeElement.asType()),
								typeUtils.erasure(requiredType))) {
							satisfied = true;
							break;
						}
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
