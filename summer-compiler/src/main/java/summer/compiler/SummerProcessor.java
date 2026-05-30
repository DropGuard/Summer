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
import summer.core.annotation.Configuration;

@AutoService(Processor.class)
@SupportedAnnotationTypes({"summer.core.Component", "summer.core.annotation.Configuration",
		"summer.web.annotation.RestController", "summer.web.annotation.GlobalMiddleware",
		"summer.data.jdbc.annotation.RowModel"})
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
		if (beanCollector == null) {
			beanCollector = new BeanCollectorImpl(allBeans, processingEnv);
		}

		if (roundEnv.processingOver()) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
					"[Summer AOT] Processing over. Collected " + allBeans.size() + " beans.");
			return false;
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
			try {
				generateAotContext();
			} catch (IOException e) {
				error("AOT generation failed: " + e.getMessage(), null);
			}
			aotGenerated = true;
		}

		return true;
	}

	private void generateAotContext() throws IOException {
		CompositeIndex index = JandexDiscovery.loadIndex();

		JandexDiscovery.discoverFrameworkBeans(allBeans, index, processingEnv, beanCollector);
		JandexDiscovery.discoverTransitiveDependencies(allBeans, index, processingEnv, beanCollector);

		ConditionalEvaluator.resolveReplacements(allBeans, processingEnv);
		ConditionalEvaluator.evaluateConditions(allBeans, processingEnv);
		beanCollector.resolveVariableNameConflicts();
		AopAnalyzer.analyze(allBeans, processingEnv);

		DependencyResolver resolver = new DependencyResolver(processingEnv.getMessager());
		List<BeanDefinition> sorted = resolver.resolve(allBeans);

		if (sorted.isEmpty()) {
			error("Dependency resolution failed — cannot generate AOT context.", null);
			return;
		}

		for (BeanDefinition bean : sorted) {
			if (bean.needsProxy()) {
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
			if (b instanceof AptBeanDefinition apt)
				return AnnotationHelper.hasAnnotation(apt.typeElement, "summer.web.annotation.RestController");
			return false;
		});
		boolean hasExceptionHandler = sorted.stream().anyMatch(b -> {
			if (b instanceof AptBeanDefinition apt)
				return javax.lang.model.util.ElementFilter.methodsIn(apt.typeElement.getEnclosedElements()).stream()
						.anyMatch(m -> AnnotationHelper.hasAnnotation(m, "summer.web.annotation.ExceptionHandler"));
			return false;
		});
		return hasWebController || hasExceptionHandler;
	}

	private void error(String msg, Element element) {
		processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
	}
}
