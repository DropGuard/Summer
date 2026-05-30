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

	static void analyze(List<BeanDefinition> allBeans, ProcessingEnvironment processingEnv) {
		Types typeUtils = processingEnv.getTypeUtils();
		TypeElement interceptorType = processingEnv.getElementUtils().getTypeElement("summer.aop.MethodInterceptor");
		if (interceptorType == null)
			return;

		List<BeanDefinition> interceptorBeans = new ArrayList<>();
		for (BeanDefinition bean : allBeans) {
			if (bean instanceof AptBeanDefinition apt) {
				if (typeUtils.isAssignable(typeUtils.erasure(apt.typeElement.asType()),
						typeUtils.erasure(interceptorType.asType()))) {
					interceptorBeans.add(bean);
				}
			}
		}

		if (interceptorBeans.isEmpty())
			return;

		for (BeanDefinition bean : allBeans) {
			if (interceptorBeans.contains(bean))
				continue;
			if (bean.kind() == BeanDefinition.Kind.FACTORY_PRODUCT)
				continue;
			if (bean.interfaceNames().isEmpty())
				continue;
			if (!(bean instanceof AptBeanDefinition aptBean))
				continue;

			boolean hasIntercepted = AotProxyGenerator.beanHasMethodsWithAnnotation(aptBean.typeElement,
					"summer.aop.Intercepted");

			boolean matchesAnyStaticTrigger = false;
			List<BeanDefinition> matchingStaticInterceptors = new ArrayList<>();
			List<BeanDefinition> dynamicInterceptors = new ArrayList<>();

			for (BeanDefinition interceptor : interceptorBeans) {
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
				bean.setNeedsProxy(true);
				bean.interceptors().addAll(matchingStaticInterceptors);
				bean.interceptors().addAll(dynamicInterceptors);
			}
		}
	}
}
