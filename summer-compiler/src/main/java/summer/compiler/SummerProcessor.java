package summer.compiler;

import com.google.auto.service.AutoService;
import java.io.IOException;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import org.jboss.jandex.CompositeIndex;
import summer.core.Component;
import summer.core.SummerException;
import summer.core.annotation.Configuration;
import summer.core.config.ConfigurationProperties;

@AutoService(Processor.class)
@SupportedAnnotationTypes({"summer.core.Component", "summer.core.annotation.Configuration",
		"summer.web.annotation.RestController", "summer.web.annotation.GlobalMiddleware",
		"summer.data.jdbc.annotation.RowModel", "summer.core.config.ConfigurationProperties"})
public class SummerProcessor extends AbstractProcessor {

	private final List<BeanDefinition> allBeans = new ArrayList<>();
	private BeanCollectorImpl beanCollector;
	private boolean aotGenerated = false;
	private boolean generatedNewTypesInThisRound = false;

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (roundEnv.processingOver()) {
			return false;
		}

		try {
			if (beanCollector == null) {
				beanCollector = new BeanCollectorImpl(allBeans, processingEnv);
			}

			generatedNewTypesInThisRound = false;

			processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
					"[Summer AOT] Round with annotations: " + annotations);

			for (Element e : roundEnv.getElementsAnnotatedWith(Component.class)) {
				if (e.getKind() == ElementKind.CLASS) {
					beanCollector.collectComponent((TypeElement) e);
				}
			}

			for (Element e : roundEnv.getElementsAnnotatedWith(Configuration.class)) {
				if (e.getKind() == ElementKind.CLASS) {
					TypeElement configClass = (TypeElement) e;
					beanCollector.collectConfiguration(configClass);
					if (ProviderGenerator.generate(configClass, processingEnv)) {
						generatedNewTypesInThisRound = true;
					}
				}
			}

			// Process @ConfigurationProperties records
			for (Element e : roundEnv.getElementsAnnotatedWith(ConfigurationProperties.class)) {
				if (e.getKind() == ElementKind.RECORD || e.getKind() == ElementKind.CLASS) {
					TypeElement configType = (TypeElement) e;
					if (ConfigPropertiesGenerator.generate(configType, processingEnv)) {
						generatedNewTypesInThisRound = true;
					}
				}
			}

			beanCollector.collectByAnnotationName("summer.web.annotation.RestController", roundEnv);
			beanCollector.collectByAnnotationName("summer.web.annotation.GlobalMiddleware", roundEnv);

			TypeElement rowModelType = processingEnv.getElementUtils()
					.getTypeElement("summer.data.jdbc.annotation.RowModel");
			if (rowModelType != null) {
				for (Element e : roundEnv.getElementsAnnotatedWith(rowModelType)) {
					if (e.getKind() == ElementKind.RECORD || e.getKind() == ElementKind.CLASS) {
						if (RowMapperGenerator.generate((TypeElement) e, processingEnv)) {
							generatedNewTypesInThisRound = true;
						}
					}
				}
			}

			processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
					"[Summer AOT] After round: " + allBeans.size() + " beans collected so far.");

			if (!generatedNewTypesInThisRound && !allBeans.isEmpty() && !aotGenerated) {
				processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
						"[Summer AOT] Generating AOT context in round where no new types were generated.");
				generateAotContext();
				aotGenerated = true;
			}

			return true;
		} catch (SummerException e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "[Summer] " + e.getMessage());
			return false;
		} catch (Exception e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
					"[Summer] Internal error: " + e.getMessage());
			return false;
		}
	}

	private void generateAotContext() throws IOException {
		CompositeIndex index = JandexDiscovery.loadIndex();

		// Create Jandex collector that creates BeanDefinition from ClassInfo
		JandexBeanCollector jandexCollector = new JandexBeanCollector(allBeans);
		JandexDiscovery.discoverBeansFromIndex(allBeans, index, jandexCollector);

		ConditionalEvaluator.resolveReplacements(allBeans, processingEnv);
		ConditionalEvaluator.evaluateConditions(allBeans, processingEnv);
		beanCollector.resolveVariableNameConflicts();
		AopAnalyzer.analyze(allBeans, processingEnv);

		DependencyResolver resolver = new DependencyResolver();
		List<BeanDefinition> sorted = resolver.resolve(allBeans);

		for (BeanDefinition bean : sorted) {
			if (bean.needsProxy) {
				AotProxyGenerator.generate(bean, processingEnv);
				generatedNewTypesInThisRound = true;
			}
		}

		boolean generateWebAdapter = shouldGenerateWebAdapter(sorted);

		if (generateWebAdapter) {
			RouteAdapterGenerator.generate(sorted, processingEnv);
			generatedNewTypesInThisRound = true;
		}

		AotContextGenerator generator = new AotContextGenerator(processingEnv.getFiler(), processingEnv.getMessager());
		generator.generate(sorted, generateWebAdapter);
	}

	private boolean shouldGenerateWebAdapter(List<BeanDefinition> sorted) {
		TypeElement restControllerType = processingEnv.getElementUtils()
				.getTypeElement("summer.web.annotation.RestController");
		if (restControllerType == null)
			return false;

		boolean hasWebController = sorted.stream().anyMatch(b -> {
			TypeElement te = processingEnv.getElementUtils().getTypeElement(b.qualifiedName);
			return te != null && AnnotationHelper.hasAnnotation(te, "summer.web.annotation.RestController");
		});
		boolean hasExceptionHandler = sorted.stream().anyMatch(b -> {
			TypeElement te = processingEnv.getElementUtils().getTypeElement(b.qualifiedName);
			if (te == null)
				return false;
			return javax.lang.model.util.ElementFilter.methodsIn(te.getEnclosedElements()).stream()
					.anyMatch(m -> AnnotationHelper.hasAnnotation(m, "summer.web.annotation.ExceptionHandler"));
		});
		return hasWebController || hasExceptionHandler;
	}

	/**
	 * Jandex-specific collector that creates BeanDefinition from ClassInfo.
	 */
	private class JandexBeanCollector implements JandexDiscovery.BeanCollector {
		private final List<BeanDefinition> allBeans;

		JandexBeanCollector(List<BeanDefinition> allBeans) {
			this.allBeans = allBeans;
		}

		@Override
		public void collectComponent(org.jboss.jandex.ClassInfo ci) {
			if (ci.isAnnotation())
				return; // Skip annotation classes

			BeanDefinition bean = new BeanDefinition(BeanDefinition.Kind.COMPONENT, ci.name().toString(),
					ci.simpleName());

			org.jboss.jandex.MethodInfo ctor = ci.firstMethod("<init>");
			if (ctor != null) {
				for (int i = 0; i < ctor.parametersCount(); i++) {
					bean.constructorParamTypes.add(ctor.parameterType(i).name().toString());
				}
			}

			for (org.jboss.jandex.Type iface : ci.interfaceTypes()) {
				bean.interfaceNames.add(iface.name().toString());
			}

			bean.variableName = toVariableName(ci.simpleName());
			allBeans.add(bean);
		}

		@Override
		public void collectConfiguration(org.jboss.jandex.ClassInfo ci) {
			BeanDefinition configBean = new BeanDefinition(BeanDefinition.Kind.CONFIGURATION, ci.name().toString(),
					ci.simpleName());

			org.jboss.jandex.MethodInfo ctor = ci.firstMethod("<init>");
			if (ctor != null) {
				for (int i = 0; i < ctor.parametersCount(); i++) {
					configBean.constructorParamTypes.add(ctor.parameterType(i).name().toString());
				}
			}

			configBean.variableName = toVariableName(ci.simpleName());
			allBeans.add(configBean);

			for (org.jboss.jandex.MethodInfo method : ci.methods()) {
				if (method.hasAnnotation(org.jboss.jandex.DotName.createSimple("summer.core.annotation.Bean"))) {
					collectFactoryProduct(ci, method);
				}
			}
		}

		@Override
		public boolean alreadyCollectedByName(String qualifiedName) {
			return allBeans.stream().anyMatch(b -> b.qualifiedName.equals(qualifiedName));
		}

		private void collectFactoryProduct(org.jboss.jandex.ClassInfo configClass, org.jboss.jandex.MethodInfo method) {
			org.jboss.jandex.Type returnType = method.returnType();
			if (returnType == null)
				return;

			BeanDefinition bean = new BeanDefinition(BeanDefinition.Kind.FACTORY_PRODUCT, returnType.name().toString(),
					returnType.name().withoutPackagePrefix());

			bean.configClassName = configClass.name().toString();
			bean.producerMethodName = method.name();
			bean.variableName = toVariableName(returnType.name().withoutPackagePrefix());

			for (int i = 0; i < method.parametersCount(); i++) {
				bean.producerParamTypes.add(method.parameterType(i).name().toString());
			}

			allBeans.add(bean);
		}

		private static String toVariableName(String simpleName) {
			if (simpleName.isEmpty())
				return "bean";
			return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
		}
	}
}
