package summer.core.bean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.NoSuchBeanException;

/**
 * Shared condition evaluator used by both Runtime and AOT engines.
 *
 * <p>
 * Four-phase evaluation:
 * </p>
 * <ol>
 * <li>Collect {@code @ConditionalOnBean} requirements</li>
 * <li>Evaluate {@code @ConditionalOnBean} in topological order, remove
 * unsatisfied beans</li>
 * <li>Resolve {@code @Replaces} (class-level and method-level), remove replaced
 * beans</li>
 * <li>Remove orphaned {@code @Bean} method products</li>
 * </ol>
 *
 * <p>
 * Reads annotations via Jandex {@link IndexView}, works for both Runtime and
 * AOT engines.
 * </p>
 */
public final class SharedConditionEvaluator {

	private static final Logger log = LoggerFactory.getLogger(SharedConditionEvaluator.class);
	private static final DotName CONDITIONAL_DOT = DotName.createSimple("summer.core.annotation.ConditionalOnBean");
	private static final DotName REPLACES_DOT = DotName.createSimple("summer.core.annotation.Replaces");

	private final IndexView index;

	public SharedConditionEvaluator(IndexView index) {
		this.index = index;
	}

	/**
	 * Evaluates conditions and removes unsatisfied beans.
	 *
	 * @param beans
	 *            bean list (mutated in place)
	 */
	public void evaluate(List<BeanDefinition> beans) {
		Map<String, String> requiredTypes = collectConditionalRequirements(beans);
		List<BeanDefinition> topoOrder = buildTopologicalOrder(beans, requiredTypes);
		resolveConditionalOnBean(beans, topoOrder, requiredTypes);
		resolveReplaces(beans);
		removeOrphanedFactoryProducts(beans);
	}

	// ── Collect @ConditionalOnBean requirements ───────────────────

	private Map<String, String> collectConditionalRequirements(List<BeanDefinition> beans) {
		Map<String, String> requiredTypes = new HashMap<>();
		for (BeanDefinition bean : beans) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;

			AnnotationInstance condAnn = ci.annotation(CONDITIONAL_DOT);
			if (condAnn != null) {
				requiredTypes.put(bean.qualifiedName, condAnn.value().asClass().name().toString());
			}

			if (bean.isFactoryMethod()) {
				ClassInfo configCi = index.getClassByName(DotName.createSimple(bean.configClassName));
				if (configCi != null) {
					for (MethodInfo method : configCi.methods()) {
						if (method.name().equals(bean.producerMethodName) && method.hasAnnotation(CONDITIONAL_DOT)) {
							requiredTypes.put(bean.qualifiedName,
									method.annotation(CONDITIONAL_DOT).value().asClass().name().toString());
						}
					}
				}
			}
		}
		return requiredTypes;
	}

	// ── Topological sort ──────────────────────────────────────────

	private List<BeanDefinition> buildTopologicalOrder(List<BeanDefinition> beans, Map<String, String> requiredTypes) {
		Map<BeanDefinition, Set<BeanDefinition>> deps = new HashMap<>();
		for (BeanDefinition bean : beans) {
			String required = requiredTypes.get(bean.qualifiedName);
			if (required == null)
				continue;

			Set<BeanDefinition> matches = new HashSet<>();
			for (BeanDefinition other : beans) {
				if (other.qualifiedName.equals(required)) {
					matches.add(other);
				} else if (other.interfaceNames.contains(required)) {
					matches.add(other);
				}
			}
			if (!matches.isEmpty()) {
				deps.put(bean, matches);
			}
		}

		Set<BeanDefinition> visited = new HashSet<>();
		Set<BeanDefinition> inStack = new HashSet<>();
		List<BeanDefinition> order = new ArrayList<>();
		for (BeanDefinition bean : beans) {
			dfs(bean, deps, visited, inStack, order);
		}
		return order;
	}

	private void dfs(BeanDefinition bean, Map<BeanDefinition, Set<BeanDefinition>> deps, Set<BeanDefinition> visited,
			Set<BeanDefinition> inStack, List<BeanDefinition> order) {
		if (visited.contains(bean))
			return;
		visited.add(bean);
		inStack.add(bean);

		Set<BeanDefinition> beanDeps = deps.getOrDefault(bean, Set.of());
		for (BeanDefinition dep : beanDeps) {
			if (!visited.contains(dep)) {
				dfs(dep, deps, visited, inStack, order);
			}
		}

		inStack.remove(bean);
		order.add(bean);
	}

	// ── @Replaces ─────────────────────────────────────────────────

	private void resolveReplaces(List<BeanDefinition> beans) {
		// First pass: mark method-level @Replaces
		for (BeanDefinition bean : beans) {
			if (!bean.isFactoryMethod())
				continue;
			log.debug("[Summer] Checking method-level @Replaces for {}.{}", bean.configClassName,
					bean.producerMethodName);
			String target = resolveMethodLevelReplaces(bean);
			if (target != null) {
				log.debug("[Summer] Found method-level @Replaces: {}.{} replaces {}", bean.configClassName,
						bean.producerMethodName, target);
				bean.replacesReturnType = target;
			}
		}

		// Second pass: collect all replaced beans
		List<BeanDefinition> replaced = new ArrayList<>();
		for (BeanDefinition bean : beans) {
			// Class-level: @Replaces on the replacement class itself
			String targetName = resolveClassLevelReplaces(bean);
			if (targetName != null) {
				BeanDefinition target = findBeanByName(beans, targetName);
				if (target == null)
					throw new NoSuchBeanException("@Replaces target not found: " + targetName);
				log.debug("[Summer] Class-level @Replaces: {} replaces {}", bean.qualifiedName, targetName);
				replaced.add(target);
			}
			// Method-level: @Replaces on @Bean method
			if (bean.replacesReturnType != null) {
				BeanDefinition target = findBeanByReturnType(beans, bean.replacesReturnType, bean);
				if (target == null)
					throw new NoSuchBeanException("@Replaces target not found: " + bean.replacesReturnType);
				String beanDesc = bean.isFactoryMethod()
						? bean.configClassName + "#" + bean.producerMethodName
						: bean.qualifiedName;
				String targetDesc = target.isFactoryMethod()
						? target.configClassName + "#" + target.producerMethodName
						: target.qualifiedName;
				log.debug("[Summer] Method-level @Replaces: {} replaces {}", beanDesc, targetDesc);
				replaced.add(target);
			}
		}

		log.debug("[Summer] Removing {} replaced beans", replaced.size());
		for (BeanDefinition r : replaced) {
			String desc = r.isFactoryMethod() ? r.configClassName + "#" + r.producerMethodName : r.qualifiedName;
			log.debug("[Summer]   Removing: {} ({})", desc, r.getClass().getSimpleName());
		}
		beans.removeAll(replaced);
		log.debug("[Summer] Beans after resolveReplaces: {} remaining", beans.size());
	}

	/**
	 * Resolves the class-level {@code @Replaces} target for a bean definition. The
	 * target is set by {@code BeanDefinitionFactory.toBeanDefinitions()} via
	 * reflection at build time — no Jandex lookup needed.
	 */
	private String resolveClassLevelReplaces(BeanDefinition bean) {
		return bean.replacesTargetClass;
	}

	private String resolveMethodLevelReplaces(BeanDefinition fb) {
		ClassInfo configCi = index.getClassByName(DotName.createSimple(fb.configClassName));
		if (configCi == null) {
			log.debug("[Summer] resolveMethodLevelReplaces: configCi is null for {}", fb.configClassName);
			return null;
		}

		for (MethodInfo method : configCi.methods()) {
			if (!method.name().equals(fb.producerMethodName))
				continue;
			log.debug("[Summer] resolveMethodLevelReplaces: found method {}, hasReplaces={}", method.name(),
					method.hasAnnotation(REPLACES_DOT));
			if (!method.hasAnnotation(REPLACES_DOT))
				continue;
			AnnotationInstance ann = method.annotation(REPLACES_DOT);
			if (ann != null) {
				return ann.value().asClass().name().toString();
			}
		}
		return null;
	}

	// ── @ConditionalOnBean ────────────────────────────────────────

	private void resolveConditionalOnBean(List<BeanDefinition> beans, List<BeanDefinition> topoOrder,
			Map<String, String> requiredTypes) {
		Set<String> available = new HashSet<>();
		for (BeanDefinition bean : beans) {
			available.add(bean.qualifiedName);
			available.addAll(bean.interfaceNames);
		}

		for (BeanDefinition bean : topoOrder) {
			if (!beans.contains(bean))
				continue;

			String required = requiredTypes.get(bean.qualifiedName);
			if (required == null)
				continue;

			if (!available.contains(required)) {
				available.remove(bean.qualifiedName);
				available.removeAll(bean.interfaceNames);
				beans.remove(bean);
			}
		}
	}

	private void removeOrphanedFactoryProducts(List<BeanDefinition> beans) {
		Set<String> allBeanNames = new HashSet<>();
		for (BeanDefinition bean : beans) {
			allBeanNames.add(bean.qualifiedName);
		}
		beans.removeIf(b -> b.isFactoryMethod() && !allBeanNames.contains(b.configClassName));
	}

	// ── Helpers ───────────────────────────────────────────────────

	private BeanDefinition findBeanByName(List<BeanDefinition> beans, String name) {
		for (BeanDefinition bean : beans) {
			if (bean.qualifiedName.equals(name))
				return bean;
		}
		return null;
	}

	private BeanDefinition findBeanByReturnType(List<BeanDefinition> beans, String returnType,
			BeanDefinition replacement) {
		BeanDefinition found = null;
		for (BeanDefinition bean : beans) {
			if (bean == replacement)
				continue;
			if (bean.isFactoryMethod() && bean.qualifiedName.equals(returnType)) {
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
