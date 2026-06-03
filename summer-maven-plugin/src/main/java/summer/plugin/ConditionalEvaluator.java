package summer.plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/**
 * Evaluates @ConditionalOnBean conditions and removes beans whose conditions
 * are not satisfied.
 * 
 * <p>
 * This is the Maven plugin equivalent of the old ConditionalEvaluator that used
 * ProcessingEnvironment. This version uses Jandex Index instead.
 * </p>
 */
public final class ConditionalEvaluator {

	private ConditionalEvaluator() {
	}

	/**
	 * Evaluate @ConditionalOnBean conditions and remove unsatisfied beans.
	 * 
	 * @param beans
	 *              list of bean definitions (will be modified)
	 * @param index
	 *              Jandex index for looking up classes
	 */
	public static void evaluate(List<BeanDefinition> beans, IndexView index) {
		resolveConditionalOnBean(beans, index);
		resolveReplaces(beans, index);
	}

	private static void resolveConditionalOnBean(List<BeanDefinition> beans, IndexView index) {
		DotName conditionalDot = DotName.createSimple("summer.core.annotation.ConditionalOnBean");

		// 1. Build dependency map: bean qualifiedName → required type
		Map<String, String> dependencies = new HashMap<>();
		for (BeanDefinition bean : beans) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;
			AnnotationInstance condAnn = ci.annotation(conditionalDot);
			if (condAnn != null) {
				dependencies.put(bean.qualifiedName, condAnn.value().asClass().name().toString());
			}
		}

		// 2. Collect all available types (qualifiedName + interfaceNames)
		Set<String> available = new HashSet<>();
		for (BeanDefinition bean : beans) {
			available.add(bean.qualifiedName);
			available.addAll(bean.interfaceNames);
		}

		// 3. BFS: remove beans whose conditions are not satisfied, cascade removal
		Queue<String> toRemove = new ArrayDeque<>();
		for (BeanDefinition bean : beans) {
			String required = dependencies.get(bean.qualifiedName);
			if (required != null && !available.contains(required)) {
				toRemove.add(bean.qualifiedName);
			}
		}

		while (!toRemove.isEmpty()) {
			String name = toRemove.poll();
			if (!available.remove(name))
				continue;
			beans.removeIf(b -> b.qualifiedName.equals(name));

			// Check if removing this bean causes new failures
			for (BeanDefinition bean : beans) {
				String required = dependencies.get(bean.qualifiedName);
				if (required != null && required.equals(name)) {
					toRemove.add(bean.qualifiedName);
				}
			}
		}
	}

	private static void resolveReplaces(List<BeanDefinition> beans, IndexView index) {
		DotName replacesDot = DotName.createSimple("summer.core.annotation.Replaces");
		List<BeanDefinition> replaced = new ArrayList<>();

		for (BeanDefinition bean : beans) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;

			AnnotationInstance replacesAnn = ci.annotation(replacesDot);
			if (replacesAnn == null)
				continue;

			String targetName = replacesAnn.value().asClass().name().toString();
			for (BeanDefinition other : beans) {
				if (other != bean && other.qualifiedName.equals(targetName)) {
					replaced.add(other);
					break;
				}
			}
		}
		beans.removeAll(replaced);
	}
}
