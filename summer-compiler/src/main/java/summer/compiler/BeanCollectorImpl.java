package summer.compiler;

import java.util.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import summer.core.ErrorCode;
import summer.core.SummerException;
import summer.core.annotation.Bean;

final class BeanCollectorImpl implements JandexDiscovery.BeanCollector {

	private final List<AptBeanDefinition> allBeans;
	private final ProcessingEnvironment processingEnv;

	BeanCollectorImpl(List<AptBeanDefinition> allBeans, ProcessingEnvironment processingEnv) {
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
			throw new SummerException(ErrorCode.BEAN_CREATION_FAILED,
					"Bean class must be public for AOT compilation: " + typeElement.getQualifiedName());
		}

		AptBeanDefinition bean = new AptBeanDefinition(AptBeanDefinition.Kind.COMPONENT, typeElement);
		fillConstructorInfo(bean);
		fillInterfaces(bean);
		bean.isAutoCloseable = isAutoCloseable(typeElement);
		bean.variableName = toVariableName(typeElement.getSimpleName().toString());
		allBeans.add(bean);
	}

	@Override
	public void collectConfiguration(TypeElement typeElement) {
		if (alreadyCollected(typeElement))
			return;
		if (isTestInnerClass(typeElement))
			return;

		if (!typeElement.getModifiers().contains(Modifier.PUBLIC)) {
			throw new SummerException(ErrorCode.BEAN_CREATION_FAILED,
					"Configuration class must be public for AOT compilation: " + typeElement.getQualifiedName());
		}

		AptBeanDefinition configBean = new AptBeanDefinition(AptBeanDefinition.Kind.CONFIGURATION, typeElement);
		fillConstructorInfo(configBean);
		configBean.isAutoCloseable = isAutoCloseable(typeElement);
		configBean.variableName = toVariableName(typeElement.getSimpleName().toString());
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
		for (AptBeanDefinition bean : allBeans) {
			String base = bean.variableName;
			String name = base;
			int suffix = 2;
			while (!usedNames.add(name)) {
				name = base + suffix++;
			}
			bean.variableName = name;
		}
	}

	private void collectFactoryProduct(TypeElement configClass, ExecutableElement method) {
		TypeMirror returnType = method.getReturnType();
		TypeElement returnElement = asTypeElement(returnType);
		if (returnElement == null) {
			throw new SummerException(ErrorCode.BEAN_CREATION_FAILED,
					"@Bean method return type is not a declared type: " + returnType);
		}

		AptBeanDefinition bean = new AptBeanDefinition(AptBeanDefinition.Kind.FACTORY_PRODUCT, returnElement);
		bean.configClass = configClass;
		bean.producerMethod = method;
		bean.producedType = returnType;
		bean.isAutoCloseable = isAutoCloseable(returnElement);
		bean.variableName = toVariableName(returnElement.getSimpleName().toString());

		for (VariableElement param : method.getParameters()) {
			bean.producerParamTypeMirrors.add(param.asType());
		}

		allBeans.add(bean);
	}

	private void fillConstructorInfo(AptBeanDefinition bean) {
		List<ExecutableElement> constructors = ElementFilter.constructorsIn(bean.typeElement.getEnclosedElements())
				.stream().filter(c -> c.getModifiers().contains(Modifier.PUBLIC)).toList();

		if (constructors.isEmpty()) {
			throw new SummerException(ErrorCode.BEAN_CREATION_FAILED,
					"No public constructor found for " + bean.qualifiedName());
		}
		if (constructors.size() != 1) {
			throw new SummerException(ErrorCode.BEAN_CREATION_FAILED,
					"Component " + bean.qualifiedName() + " must have exactly ONE public constructor. Found: "
							+ constructors.size());
		}
		ExecutableElement ctor = constructors.getFirst();

		bean.constructor = ctor;
		for (VariableElement param : ctor.getParameters()) {
			bean.constructorParamTypeMirrors.add(param.asType());
		}
	}

	private void fillInterfaces(AptBeanDefinition bean) {
		collectInterfaces(bean.typeElement, bean.interfaceMirrors, new HashSet<>());
	}

	private void collectInterfaces(TypeElement type, List<TypeMirror> result, Set<String> visited) {
		for (TypeMirror iface : type.getInterfaces()) {
			String name = iface.toString();
			if (visited.add(name)) {
				result.add(iface);
				// Recursively collect parent interfaces
				Element element = processingEnv.getTypeUtils().asElement(iface);
				if (element instanceof TypeElement ifaceElement) {
					collectInterfaces(ifaceElement, result, visited);
				}
			}
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
}
