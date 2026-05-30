package summer.compiler;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

final class AopAnalyzer {

	private AopAnalyzer() {
	}

	static void analyze(List<AptBeanDefinition> allBeans, ProcessingEnvironment processingEnv) {
		Types typeUtils = processingEnv.getTypeUtils();
		TypeElement interceptorType = processingEnv.getElementUtils().getTypeElement("summer.aop.MethodInterceptor");
		if (interceptorType == null)
			return;

		List<AptBeanDefinition> interceptorBeans = new ArrayList<>();
		for (AptBeanDefinition bean : allBeans) {
			if (bean instanceof AptBeanDefinition apt) {
				if (typeUtils.isAssignable(typeUtils.erasure(apt.typeElement.asType()),
						typeUtils.erasure(interceptorType.asType()))) {
					interceptorBeans.add(bean);
				}
			}
		}

		if (interceptorBeans.isEmpty())
			return;

		for (AptBeanDefinition bean : allBeans) {
			if (interceptorBeans.contains(bean))
				continue;
			if (bean.kind == AptBeanDefinition.Kind.FACTORY_PRODUCT)
				continue;
			if (bean.interfaceNames().isEmpty())
				continue;
			if (!(bean instanceof AptBeanDefinition aptBean))
				continue;

			boolean hasIntercepted = AotProxyGenerator.beanHasMethodsWithAnnotation(aptBean.typeElement,
					"summer.aop.Intercepted");

			boolean matchesAnyStaticTrigger = false;
			List<AptBeanDefinition> matchingStaticInterceptors = new ArrayList<>();
			List<AptBeanDefinition> dynamicInterceptors = new ArrayList<>();

			for (AptBeanDefinition interceptor : interceptorBeans) {
				AptBeanDefinition aptInterceptor = (AptBeanDefinition) interceptor;
				List<TypeMirror> targets = AotProxyGenerator.getInterceptsAnnotations(aptInterceptor.typeElement);
				if (targets.isEmpty()) {
					dynamicInterceptors.add(interceptor);
				} else {
					if (AotProxyGenerator.beanHasAnnotatedMethods(aptBean.typeElement, targets, processingEnv)) {
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
}
