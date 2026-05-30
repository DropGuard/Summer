package summer.compiler;

import java.util.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import summer.core.annotation.Bean;

final class BeanCollectorImpl implements JandexDiscovery.BeanCollector {

	private final List<BeanDefinition> allBeans;
	private final ProcessingEnvironment processingEnv;

	BeanCollectorImpl(List<BeanDefinition> allBeans, ProcessingEnvironment processingEnv) {
		this.allBeans = allBeans;
		this.processingEnv = processingEnv;
	}

	@Override
	public void collectComponent(TypeElement typeElement) {
		if (alreadyCollected(typeElement))
			return;
		if (isTestInnerClass(typeElement))
			return;
		if (typeElement.getKind() != ElementKind.CLASS)
			return;
		if (isGeneratedProvider(typeElement))
			return;

		if (!typeElement.getModifiers().contains(Modifier.PUBLIC)) {
			error("Bean class must be public for AOT compilation: " + typeElement.getQualifiedName(), typeElement);
			return;
		}

		AptBeanDefinition bean = new AptBeanDefinition(BeanDefinition.Kind.COMPONENT, typeElement);
		fillConstructorInfo(bean);
		fillInterfaces(bean);
		bean.setAutoCloseable(isAutoCloseable(typeElement));
		bean.setVariableName(toVariableName(typeElement.getSimpleName().toString()));
		allBeans.add(bean);
	}

	@Override
	public void collectConfiguration(TypeElement typeElement) {
		if (alreadyCollected(typeElement))
			return;
		if (isTestInnerClass(typeElement))
			return;

		if (!typeElement.getModifiers().contains(Modifier.PUBLIC)) {
			error("Configuration class must be public for AOT compilation: " + typeElement.getQualifiedName(),
					typeElement);
			return;
		}

		AptBeanDefinition configBean = new AptBeanDefinition(BeanDefinition.Kind.CONFIGURATION, typeElement);
		fillConstructorInfo(configBean);
		configBean.setAutoCloseable(isAutoCloseable(typeElement));
		configBean.setVariableName(toVariableName(typeElement.getSimpleName().toString()));
		allBeans.add(configBean);

		for (ExecutableElement method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
			if (method.getAnnotation(Bean.class) != null) {
				collectFactoryProduct(typeElement, method);
			}
		}
	}

	@Override
	public boolean alreadyCollected(TypeElement typeElement) {
		String qn = typeElement.getQualifiedName().toString();
		return allBeans.stream().anyMatch(b -> b.qualifiedName().equals(qn));
	}

	@Override
	public boolean alreadyCollectedByName(String qualifiedName) {
		return allBeans.stream().anyMatch(b -> b.qualifiedName().equals(qualifiedName));
	}

	void collectByAnnotationName(String annotationFqn, javax.annotation.processing.RoundEnvironment roundEnv) {
		TypeElement annotationType = processingEnv.getElementUtils().getTypeElement(annotationFqn);
		if (annotationType == null)
			return;

		for (Element e : roundEnv.getElementsAnnotatedWith(annotationType)) {
			if (e.getKind() == ElementKind.CLASS) {
				collectComponent((TypeElement) e);
			}
		}
	}

	void resolveVariableNameConflicts() {
		Set<String> usedNames = new HashSet<>();
		for (BeanDefinition bean : allBeans) {
			String base = bean.variableName();
			String name = base;
			int suffix = 2;
			while (!usedNames.add(name)) {
				name = base + suffix++;
			}
			bean.setVariableName(name);
		}
	}

	private void collectFactoryProduct(TypeElement configClass, ExecutableElement method) {
		TypeMirror returnType = method.getReturnType();
		TypeElement returnElement = asTypeElement(returnType);
		if (returnElement == null) {
			error("@Bean method return type is not a declared type: " + returnType, method);
			return;
		}

		AptBeanDefinition bean = new AptBeanDefinition(BeanDefinition.Kind.FACTORY_PRODUCT, returnElement);
		bean.configClass = configClass;
		bean.producerMethod = method;
		bean.producedType = returnType;
		bean.setAutoCloseable(isAutoCloseable(returnElement));
		bean.setVariableName(toVariableName(returnElement.getSimpleName().toString()));

		for (VariableElement param : method.getParameters()) {
			bean.producerParamTypeMirrors.add(param.asType());
		}

		allBeans.add(bean);
	}

	private void fillConstructorInfo(AptBeanDefinition bean) {
		List<ExecutableElement> constructors = ElementFilter.constructorsIn(bean.typeElement.getEnclosedElements())
				.stream().filter(c -> c.getModifiers().contains(Modifier.PUBLIC)).toList();

		if (constructors.isEmpty()) {
			error("No public constructor found for " + bean.qualifiedName(), bean.typeElement);
			return;
		}
		if (constructors.size() != 1) {
			error("Component " + bean.qualifiedName() + " must have exactly ONE public constructor. Found: "
					+ constructors.size(), bean.typeElement);
			return;
		}
		ExecutableElement ctor = constructors.getFirst();

		bean.constructor = ctor;
		for (VariableElement param : ctor.getParameters()) {
			bean.constructorParamTypeMirrors.add(param.asType());
		}
	}

	private void fillInterfaces(AptBeanDefinition bean) {
		for (TypeMirror iface : bean.typeElement.getInterfaces()) {
			bean.interfaceMirrors.add(iface);
		}
	}

	private String toVariableName(String simpleName) {
		if (simpleName.isEmpty())
			return "bean";
		return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
	}

	private boolean isAutoCloseable(TypeElement typeElement) {
		TypeElement acElement = processingEnv.getElementUtils().getTypeElement("java.lang.AutoCloseable");
		if (acElement == null)
			return false;
		return processingEnv.getTypeUtils().isAssignable(processingEnv.getTypeUtils().erasure(typeElement.asType()),
				processingEnv.getTypeUtils().erasure(acElement.asType()));
	}

	private boolean isGeneratedProvider(TypeElement typeElement) {
		String name = typeElement.getSimpleName().toString();
		if (!name.endsWith("_Provider"))
			return false;

		TypeElement providerType = processingEnv.getElementUtils().getTypeElement("summer.core.Provider");
		if (providerType == null)
			return false;

		return processingEnv.getTypeUtils().isAssignable(processingEnv.getTypeUtils().erasure(typeElement.asType()),
				processingEnv.getTypeUtils().erasure(providerType.asType()));
	}

	private boolean isTestInnerClass(TypeElement typeElement) {
		Element enclosing = typeElement.getEnclosingElement();
		if (enclosing == null || enclosing.getKind() != ElementKind.CLASS)
			return false;
		TypeElement enclosingClass = (TypeElement) enclosing;
		return !AnnotationHelper.hasAnnotation(enclosingClass, "summer.core.Component")
				&& !AnnotationHelper.hasAnnotation(enclosingClass, "summer.core.annotation.Configuration")
				&& !AnnotationHelper.hasAnnotation(enclosingClass, "summer.web.annotation.RestController");
	}

	private TypeElement asTypeElement(TypeMirror typeMirror) {
		Element element = processingEnv.getTypeUtils().asElement(typeMirror);
		return (element instanceof TypeElement te) ? te : null;
	}

	private void error(String msg, Element element) {
		processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
	}
}
