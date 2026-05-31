package summer.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import org.jboss.jandex.*;

/**
 * Jandex index loading and cross-module bean discovery. Creates BeanDefinition
 * directly from ClassInfo without requiring TypeElement.
 */
final class JandexDiscovery {

	private JandexDiscovery() {
	}

	/**
	 * Callback interface for registering discovered beans.
	 */
	interface BeanCollector {
		void collectComponent(ClassInfo classInfo);
		void collectConfiguration(ClassInfo classInfo);
		boolean alreadyCollectedByName(String qualifiedName);
	}

	/**
	 * Loads all pre-built Jandex indexes from dependency JARs.
	 */
	static CompositeIndex loadIndex() throws IOException {
		List<IndexView> indexes = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		collectIndexes(JandexDiscovery.class.getClassLoader(), indexes, seen);

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
	 * Discovers beans from Jandex indexes.
	 */
	static void discoverBeansFromIndex(List<BeanDefinition> allBeans, CompositeIndex index, BeanCollector collector) {
		DotName componentDot = DotName.createSimple("summer.core.Component");
		DotName configDot = DotName.createSimple("summer.core.annotation.Configuration");

		// Phase 1: Directly annotated beans
		for (AnnotationInstance ai : index.getAnnotations(componentDot)) {
			if (ai.target().kind() != AnnotationTarget.Kind.CLASS)
				continue;
			ClassInfo ci = ai.target().asClass();
			if (collector.alreadyCollectedByName(ci.name().toString()))
				continue;
			collector.collectComponent(ci);
		}

		for (AnnotationInstance ai : index.getAnnotations(configDot)) {
			if (ai.target().kind() != AnnotationTarget.Kind.CLASS)
				continue;
			ClassInfo ci = ai.target().asClass();
			if (collector.alreadyCollectedByName(ci.name().toString()))
				continue;
			collector.collectConfiguration(ci);
		}

		// Phase 2: Meta-annotated components
		for (AnnotationInstance metaAnn : index.getAnnotations(componentDot)) {
			if (metaAnn.target().kind() != AnnotationTarget.Kind.CLASS)
				continue;
			ClassInfo annotatedClass = metaAnn.target().asClass();
			if (!annotatedClass.isAnnotation())
				continue;

			DotName metaAnnotationName = annotatedClass.name();
			for (AnnotationInstance usage : index.getAnnotations(metaAnnotationName)) {
				if (usage.target().kind() != AnnotationTarget.Kind.CLASS)
					continue;
				ClassInfo userClass = usage.target().asClass();
				if (collector.alreadyCollectedByName(userClass.name().toString()))
					continue;
				collector.collectComponent(userClass);
			}
		}

		// Phase 3: Transitive dependencies
		boolean changed = true;
		while (changed) {
			changed = false;
			List<String> allParamTypes = new ArrayList<>();
			for (BeanDefinition bean : allBeans) {
				if (bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT) {
					allParamTypes.addAll(bean.producerParamTypes);
				} else {
					allParamTypes.addAll(bean.constructorParamTypes);
				}
			}

			for (String paramType : allParamTypes) {
				if (isBeanSatisfiedByName(paramType, allBeans))
					continue;

				ClassInfo ci = index.getClassByName(DotName.createSimple(paramType));
				if (ci != null && !collector.alreadyCollectedByName(paramType)) {
					if (tryCollectFromIndex(ci, collector)) {
						changed = true;
					}
				}
			}
		}
	}

	private static boolean isBeanSatisfiedByName(String qualifiedName, List<BeanDefinition> allBeans) {
		for (BeanDefinition b : allBeans) {
			if (b.qualifiedName.equals(qualifiedName)) {
				return true;
			}
			for (String iface : b.interfaceNames) {
				if (iface.equals(qualifiedName)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean tryCollectFromIndex(ClassInfo ci, BeanCollector collector) {
		if (collector.alreadyCollectedByName(ci.name().toString()))
			return false;

		DotName componentDot = DotName.createSimple("summer.core.Component");
		DotName configDot = DotName.createSimple("summer.core.annotation.Configuration");
		DotName restControllerDot = DotName.createSimple("summer.web.annotation.RestController");
		DotName globalMiddlewareDot = DotName.createSimple("summer.web.annotation.GlobalMiddleware");

		boolean hasComponent = ci.hasAnnotation(componentDot);
		boolean hasConfig = ci.hasAnnotation(configDot);
		boolean hasRestController = ci.hasAnnotation(restControllerDot);
		boolean hasGlobalMiddleware = ci.hasAnnotation(globalMiddlewareDot);

		if (hasComponent || hasRestController || hasGlobalMiddleware) {
			collector.collectComponent(ci);
			return true;
		} else if (hasConfig) {
			collector.collectConfiguration(ci);
			return true;
		}

		return false;
	}
}
