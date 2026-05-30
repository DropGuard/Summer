package summer.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jboss.jandex.*;

/**
 * Jandex index loading and cross-module bean discovery. Extracted from
 * SummerProcessor to separate discovery concerns from collection.
 */
final class JandexDiscovery {

	private JandexDiscovery() {
	}

	/**
	 * Callback interface for registering discovered beans.
	 */
	interface BeanCollector {
		void collectComponent(TypeElement typeElement);
		void collectConfiguration(TypeElement typeElement);
		boolean alreadyCollected(TypeElement typeElement);
		boolean alreadyCollectedByName(String qualifiedName);
	}

	/**
	 * Loads all pre-built Jandex indexes from dependency JARs. Searches both the
	 * processor classpath and the compile classpath (via thread context classloader)
	 * so framework modules are discovered even when using annotationProcessorPaths.
	 */
	static CompositeIndex loadIndex() throws IOException {
		List<IndexView> indexes = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		// Search processor classpath
		collectIndexes(JandexDiscovery.class.getClassLoader(), indexes, seen);

		// Search compile classpath (framework JARs may only be here)
		ClassLoader tccl = Thread.currentThread().getContextClassLoader();
		if (tccl != null && tccl != JandexDiscovery.class.getClassLoader()) {
			collectIndexes(tccl, indexes, seen);
		}

		return CompositeIndex.create(indexes);
	}

	private static void collectIndexes(ClassLoader cl, List<IndexView> indexes, Set<String> seen) throws IOException {
		Enumeration<URL> urls = cl.getResources("META-INF/jandex.idx");
		while (urls.hasMoreElements()) {
			URL url = urls.nextElement();
			String key = url.toString();
			if (seen.add(key)) {
				try (InputStream is = url.openStream()) {
					indexes.add(new IndexReader(is).read());
				}
			}
		}
	}

	/**
	 * Discovers framework beans from Jandex indexes: @Component, @Configuration,
	 * and meta-annotated components (@RestController, @GlobalMiddleware, etc.).
	 *
	 * For beans whose TypeElement is available (on processor classpath), uses
	 * collectComponent/collectConfiguration. For beans whose TypeElement is NOT
	 * available, emits a warning.
	 */
	static void discoverFrameworkBeans(List<AptBeanDefinition> allBeans, CompositeIndex index,
			ProcessingEnvironment processingEnv, BeanCollector collector) {
		DotName componentDot = DotName.createSimple("summer.core.Component");
		DotName configDot = DotName.createSimple("summer.core.annotation.Configuration");

		// Discover @Component-annotated classes from dependency indexes
		for (AnnotationInstance ai : index.getAnnotations(componentDot)) {
			if (ai.target().kind() != AnnotationTarget.Kind.CLASS)
				continue;
			ClassInfo ci = ai.target().asClass();
			if (collector.alreadyCollectedByName(ci.name().toString()))
				continue;

			TypeElement te = processingEnv.getElementUtils().getTypeElement(ci.name().toString());
			if (te != null) {
				collector.collectComponent(te);
			} else {
				processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.WARNING,
						"[Summer AOT] Cannot resolve type: " + ci.name() + " — ensure it is on the compile classpath.");
			}
		}

		// Discover @Configuration classes
		for (AnnotationInstance ai : index.getAnnotations(configDot)) {
			if (ai.target().kind() != AnnotationTarget.Kind.CLASS)
				continue;
			ClassInfo ci = ai.target().asClass();
			if (collector.alreadyCollectedByName(ci.name().toString()))
				continue;

			TypeElement te = processingEnv.getElementUtils().getTypeElement(ci.name().toString());
			if (te != null) {
				collector.collectConfiguration(te);
			}
			// Note: @Configuration from framework JARs without TypeElement
			// are skipped — they need @Bean method processing which requires TypeElement
		}

		// Discover meta-annotated components: @RestController, @GlobalMiddleware, etc.
		for (AnnotationInstance metaAnn : index.getAnnotations(componentDot)) {
			if (metaAnn.target().kind() != AnnotationTarget.Kind.CLASS)
				continue;
			ClassInfo annotatedClass = metaAnn.target().asClass();
			if (!annotatedClass.isAnnotation()) {
				continue;
			}
			DotName metaAnnotationName = annotatedClass.name();
			for (AnnotationInstance usage : index.getAnnotations(metaAnnotationName)) {
				if (usage.target().kind() != AnnotationTarget.Kind.CLASS)
					continue;
				ClassInfo userClass = usage.target().asClass();
				if (collector.alreadyCollectedByName(userClass.name().toString()))
					continue;

				TypeElement te = processingEnv.getElementUtils().getTypeElement(userClass.name().toString());
				if (te != null) {
					collector.collectComponent(te);
				} else {
					processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.WARNING,
							"[Summer AOT] Cannot resolve type: " + userClass.name()
									+ " — ensure it is on the compile classpath.");
				}
			}
		}
	}

	/**
	 * For each bean's constructor/producer params, if the param type isn't among
	 * collected beans, try to find it on the classpath and auto-register it.
	 */
	static void discoverTransitiveDependencies(List<AptBeanDefinition> allBeans, CompositeIndex index,
			ProcessingEnvironment processingEnv, BeanCollector collector) {
		boolean changed = true;
		while (changed) {
			changed = false;
			List<String> allParamTypes = new ArrayList<>();
			for (AptBeanDefinition bean : allBeans) {
				if (bean.kind == AptBeanDefinition.Kind.FACTORY_PRODUCT) {
					allParamTypes.addAll(bean.producerParamTypes());
				} else {
					allParamTypes.addAll(bean.constructorParamTypes());
				}
			}

			for (String paramType : allParamTypes) {
				if (isBeanSatisfiedByName(paramType, allBeans))
					continue;

				TypeElement paramElement = processingEnv.getElementUtils().getTypeElement(paramType);
				if (paramElement != null) {
					if (tryCollectFromClasspath(paramElement, allBeans, processingEnv, collector)) {
						changed = true;
						continue;
					}
				}

				// Try to find in Jandex index
				ClassInfo ci = index.getClassByName(DotName.createSimple(paramType));
				if (ci != null && !collector.alreadyCollectedByName(paramType)) {
					processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.WARNING,
							"[Summer AOT] Cannot resolve type: " + paramType
									+ " — ensure it is on the compile classpath.");
				}
			}
		}
	}

	// --- Private helpers ---

	private static boolean isBeanSatisfiedByName(String qualifiedName, List<AptBeanDefinition> allBeans) {
		for (AptBeanDefinition b : allBeans) {
			if (b.qualifiedName().equals(qualifiedName)) {
				return true;
			}
			// Check interfaces
			for (String iface : b.interfaceNames()) {
				if (iface.equals(qualifiedName)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean tryCollectFromClasspath(TypeElement element, List<AptBeanDefinition> allBeans,
			ProcessingEnvironment processingEnv, BeanCollector collector) {
		if (collector.alreadyCollected(element))
			return false;

		if (AnnotationHelper.hasAnnotation(element, "summer.core.Component")
				|| AnnotationHelper.hasAnnotation(element, "summer.core.annotation.Configuration")
				|| AnnotationHelper.hasAnnotation(element, "summer.web.annotation.RestController")
				|| AnnotationHelper.hasAnnotation(element, "summer.web.annotation.GlobalMiddleware")) {

			if (element.getAnnotation(summer.core.annotation.Configuration.class) != null) {
				collector.collectConfiguration(element);
			} else {
				collector.collectComponent(element);
			}
			return true;
		}
		return false;
	}
}
