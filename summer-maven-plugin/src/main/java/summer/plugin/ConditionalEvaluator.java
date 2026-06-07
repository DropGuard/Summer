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
import org.jboss.jandex.MethodInfo;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.NoSuchBeanException;

/**
 * Evaluates @ConditionalOnBean conditions and removes beans whose conditions
 * are not satisfied. Also handles @Replaces resolution.
 *
 * <p>
 * Supports both class-level and method-level @ConditionalOnBean annotations.
 * </p>
 */
public final class ConditionalEvaluator {

	private static final DotName CONDITIONAL_DOT = DotName.createSimple("summer.core.annotation.ConditionalOnBean");
	private static final DotName REPLACES_DOT = DotName.createSimple("summer.core.annotation.Replaces");

	private final IndexView index;

	public ConditionalEvaluator(IndexView index) {
		this.index = index;
	}

	/**
	 * Evaluate @ConditionalOnBean conditions and remove unsatisfied beans.
	 * 
	 * @param beans
	 *            list of bean definitions (will be modified)
	 */
	public void evaluate(List<BeanDefinition> beans) {
		resolveConditionalOnBean(beans);
		resolveReplaces(beans);
		removeOrphanedFactoryProducts(beans);
	}

	/**
	 * Removes FACTORY_PRODUCT beans whose parent @Configuration was removed by
	 * conditional evaluation or @Replaces. Without this, orphans survive and cause
	 * {@code DependencyResolver} to fail with "Could not find
	 * 
	 * @Configuration bean for factory product".
	 */
	private void removeOrphanedFactoryProducts(List<BeanDefinition> beans) {
		Set<String> survivingConfigs = new HashSet<>();
		for (BeanDefinition bean : beans) {
			if (bean.kind == BeanDefinition.Kind.CONFIGURATION) {
				survivingConfigs.add(bean.qualifiedName);
			}
		}
		beans.removeIf(b -> b.kind == BeanDefinition.Kind.FACTORY_PRODUCT && b.configClassName != null
				&& !survivingConfigs.contains(b.configClassName));
	}

	private void resolveConditionalOnBean(List<BeanDefinition> beans) {
		// 1. Build dependency map: bean qualifiedName -> required type
		Map<String, String> dependencies = new HashMap<>();
		for (BeanDefinition bean : beans) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;

			// Check class-level @ConditionalOnBean
			AnnotationInstance condAnn = ci.annotation(CONDITIONAL_DOT);
			if (condAnn != null) {
				dependencies.put(bean.qualifiedName, condAnn.value().asClass().name().toString());
			}

			// Check method-level @ConditionalOnBean for factory products
			if (bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT && bean.configClassName != null) {
				ClassInfo configCi = index.getClassByName(DotName.createSimple(bean.configClassName));
				if (configCi != null) {
					for (MethodInfo method : configCi.methods()) {
						if (method.name().equals(bean.producerMethodName) && method.hasAnnotation(CONDITIONAL_DOT)) {
							AnnotationInstance methodCondAnn = method.annotation(CONDITIONAL_DOT);
							if (methodCondAnn != null) {
								dependencies.put(bean.qualifiedName, methodCondAnn.value().asClass().name().toString());
							}
						}
					}
				}
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
			// Find the bean being removed to get its interfaceNames
			BeanDefinition removedBean = null;
			for (BeanDefinition bean : beans) {
				if (bean.qualifiedName.equals(name)) {
					removedBean = bean;
					break;
				}
			}
			if (removedBean == null)
				continue;

			// Remove the bean and all types it provides from available
			available.remove(name);
			available.removeAll(removedBean.interfaceNames);
			beans.remove(removedBean);

			// Check if removing this bean causes new failures
			for (BeanDefinition bean : new ArrayList<>(beans)) {
				String required = dependencies.get(bean.qualifiedName);
				if (required != null && !available.contains(required)) {
					toRemove.add(bean.qualifiedName);
				}
			}
		}
	}

	private void resolveReplaces(List<BeanDefinition> beans) {
		List<BeanDefinition> replaced = new ArrayList<>();

		// 1. Class-level @Replaces
		for (BeanDefinition bean : beans) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;

			AnnotationInstance replacesAnn = ci.annotation(REPLACES_DOT);
			if (replacesAnn == null)
				continue;

			String targetName = replacesAnn.value().asClass().name().toString();
			BeanDefinition target = findBeanByName(beans, targetName);
			if (target == null) {
				throw new NoSuchBeanException("@Replaces target not found: " + targetName);
			}
			replaced.add(target);
		}

		// 2. Method-level @Replaces (from BeanDiscovery scan)
		for (BeanDefinition bean : beans) {
			if (bean.replacesReturnType == null)
				continue;

			BeanDefinition target = findBeanByReturnType(beans, bean.replacesReturnType, bean);
			if (target == null) {
				throw new NoSuchBeanException("@Replaces target not found: " + bean.replacesReturnType);
			}
			replaced.add(target);
		}

		beans.removeAll(replaced);
	}

	private BeanDefinition findBeanByName(List<BeanDefinition> beans, String name) {
		for (BeanDefinition bean : beans) {
			if (bean.qualifiedName.equals(name)) {
				return bean;
			}
		}
		return null;
	}

	private BeanDefinition findBeanByReturnType(List<BeanDefinition> beans, String returnType,
			BeanDefinition replacement) {
		BeanDefinition found = null;
		for (BeanDefinition bean : beans) {
			if (bean == replacement)
				continue;
			if (bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT && bean.qualifiedName.equals(returnType)) {
				if (found != null) {
					throw new AmbiguousBeanException("Ambiguous @Replaces: multiple @Bean methods return " + returnType
							+ ": " + found.configClassName + "." + found.producerMethodName + " and "
							+ bean.configClassName + "." + bean.producerMethodName);
				}
				found = bean;
			}
		}
		return found;
	}
}
