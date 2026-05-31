package summer.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import summer.aop.Intercepts;

final class AopAnalyzer {

	private AopAnalyzer() {
	}

	static void analyze(List<BeanDefinition> allBeans, ProcessingEnvironment processingEnv) {
		Types typeUtils = processingEnv.getTypeUtils();
		TypeElement interceptorType = processingEnv.getElementUtils().getTypeElement("summer.aop.MethodInterceptor");
		if (interceptorType == null)
			return;

		List<BeanDefinition> interceptorBeans = new ArrayList<>();
		for (BeanDefinition bean : allBeans) {
			TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(bean.qualifiedName);
			if (typeElement == null)
				continue;
			if (typeUtils.isAssignable(typeUtils.erasure(typeElement.asType()),
					typeUtils.erasure(interceptorType.asType()))) {
				interceptorBeans.add(bean);
			}
		}

		if (interceptorBeans.isEmpty())
			return;

		for (BeanDefinition bean : allBeans) {
			if (interceptorBeans.contains(bean))
				continue;
			if (bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT)
				continue;
			if (bean.interfaceNames.isEmpty())
				continue;

			TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(bean.qualifiedName);
			if (typeElement == null)
				continue;

			boolean hasIntercepted = beanHasMethodsWithAnnotation(typeElement, "summer.aop.Intercepted");

			boolean matchesAnyStaticTrigger = false;
			List<BeanDefinition> matchingStaticInterceptors = new ArrayList<>();
			List<BeanDefinition> dynamicInterceptors = new ArrayList<>();

			for (BeanDefinition interceptor : interceptorBeans) {
				TypeElement interceptorElement = processingEnv.getElementUtils()
						.getTypeElement(interceptor.qualifiedName);
				if (interceptorElement == null)
					continue;

				List<TypeMirror> targets = getInterceptsAnnotations(interceptorElement);
				if (targets.isEmpty()) {
					dynamicInterceptors.add(interceptor);
				} else {
					if (beanHasAnnotatedMethods(typeElement, targets, processingEnv)) {
						matchesAnyStaticTrigger = true;
						matchingStaticInterceptors.add(interceptor);
					} else if (hasIntercepted) {
						matchingStaticInterceptors.add(interceptor);
					}
				}
			}

			boolean needsProxy = hasIntercepted || matchesAnyStaticTrigger;
			if (needsProxy) {
				bean.needsProxy = true;
				bean.interceptors.addAll(matchingStaticInterceptors);
				bean.interceptors.addAll(dynamicInterceptors);
			}
		}
	}

	static boolean shouldInterceptMethod(TypeElement beanClass, ExecutableElement interfaceMethod,
			List<BeanDefinition> interceptorBeans, ProcessingEnvironment processingEnv) {
		ExecutableElement targetMethod = findMatchingMethod(beanClass, interfaceMethod, processingEnv);
		if (targetMethod == null)
			return false;

		if (AnnotationHelper.hasAnnotation(targetMethod, "summer.aop.Intercepted")) {
			return true;
		}

		for (BeanDefinition interceptor : interceptorBeans) {
			TypeElement interceptorElement = processingEnv.getElementUtils().getTypeElement(interceptor.qualifiedName);
			if (interceptorElement == null)
				continue;

			List<TypeMirror> targetAnnotations = getInterceptsAnnotations(interceptorElement);
			for (AnnotationMirror am : targetMethod.getAnnotationMirrors()) {
				for (TypeMirror targetAnn : targetAnnotations) {
					if (processingEnv.getTypeUtils().isSameType(am.getAnnotationType(), targetAnn)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	static List<TypeMirror> getInterceptsAnnotations(TypeElement element) {
		Intercepts intercepts = element.getAnnotation(Intercepts.class);
		if (intercepts == null)
			return Collections.emptyList();
		try {
			intercepts.annotations();
			return Collections.emptyList();
		} catch (MirroredTypesException e) {
			return new ArrayList<>(e.getTypeMirrors());
		}
	}

	static boolean beanHasMethodsWithAnnotation(TypeElement bean, String annotationFqn) {
		for (ExecutableElement method : ElementFilter.methodsIn(bean.getEnclosedElements())) {
			if (AnnotationHelper.hasAnnotation(method, annotationFqn)) {
				return true;
			}
		}
		return false;
	}

	static boolean beanHasAnnotatedMethods(TypeElement bean, List<TypeMirror> targetAnnotations,
			ProcessingEnvironment processingEnv) {
		Types typeUtils = processingEnv.getTypeUtils();
		for (ExecutableElement method : ElementFilter.methodsIn(bean.getEnclosedElements())) {
			for (AnnotationMirror am : method.getAnnotationMirrors()) {
				for (TypeMirror target : targetAnnotations) {
					if (typeUtils.isSameType(am.getAnnotationType(), target)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	static ExecutableElement findMatchingMethod(TypeElement beanClass, ExecutableElement interfaceMethod,
			ProcessingEnvironment processingEnv) {
		Types typeUtils = processingEnv.getTypeUtils();
		String name = interfaceMethod.getSimpleName().toString();
		List<TypeMirror> interfaceParamTypes = interfaceMethod.getParameters().stream().map(VariableElement::asType)
				.toList();

		for (ExecutableElement method : ElementFilter.methodsIn(beanClass.getEnclosedElements())) {
			if (method.getSimpleName().toString().equals(name)) {
				List<TypeMirror> targetParamTypes = method.getParameters().stream().map(VariableElement::asType)
						.toList();
				if (targetParamTypes.size() == interfaceParamTypes.size()) {
					boolean match = true;
					for (int i = 0; i < targetParamTypes.size(); i++) {
						if (!typeUtils.isSameType(typeUtils.erasure(targetParamTypes.get(i)),
								typeUtils.erasure(interfaceParamTypes.get(i)))) {
							match = false;
							break;
						}
					}
					if (match) {
						return method;
					}
				}
			}
		}
		return null;
	}
}
