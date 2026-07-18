package summer.core.bean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Reads conditions and replaces from {@link BeanDefinition} fields populated
 * during discovery — no Jandex access at evaluation time.
 * </p>
 */
public final class SharedConditionEvaluator {

	private static final Logger log = LoggerFactory.getLogger(SharedConditionEvaluator.class);

	public SharedConditionEvaluator() {
	}

	/**
	 * Evaluates conditions and removes unsatisfied beans.
	 *
	 * @param beans
	 *            bean list (mutated in place)
	 */
	public void evaluate(List<BeanDefinition> beans) {
		evaluate(beans, Set.of());
	}

	/**
	 * Evaluates conditions, removes unsatisfied beans, and removes any bean whose
	 * type is mocked by a {@code @Mock} declared on the test.
	 *
	 * <p>
	 * Mock removal is a <b>discovery-stage type replacement</b> (Quarkus-style):
	 * the real implementation is taken out of the candidate set entirely, so it is
	 * never instantiated — and because nothing references it, its dependency
	 * closure is also never instantiated. This runs identically on both the Runtime
	 * and AOT engines (both funnel through this evaluator), which fixes the prior
	 * divergence where the AOT path let a concrete-class {@code @Mock} lose to the
	 * real bean via registration-order overwrite.
	 * </p>
	 *
	 * <p>
	 * {@code mockedTypes} holds the declared {@code @Mock} parameter type names
	 * (not {@code mock.getClass()}, which for a Mockito proxy differs from the
	 * target type). Matching is by exact type name and implemented interfaces, so
	 * it works without loading classes — safe for the AOT compile phase.
	 * </p>
	 *
	 * @param beans
	 *            bean list (mutated in place)
	 * @param mockedTypes
	 *            type names declared as {@code @Mock} on the test constructor
	 */
	public void evaluate(List<BeanDefinition> beans, Set<String> mockedTypes) {
		removeMockedBeans(beans, mockedTypes);
		Map<String, String> requiredTypes = collectConditionalRequirements(beans);
		List<BeanDefinition> topoOrder = buildTopologicalOrder(beans, requiredTypes);
		resolveConditionalOnBean(beans, topoOrder, requiredTypes);
		resolveReplaces(beans);
		removeOrphanedFactoryProducts(beans);
	}

	/**
	 * Removes beans whose type (or any implemented interface) is among the mocked
	 * types. The mock instance itself is supplied separately at instance-build
	 * time; removing the real definition ensures it is never instantiated.
	 */
	private void removeMockedBeans(List<BeanDefinition> beans, Set<String> mockedTypes) {
		if (mockedTypes.isEmpty()) {
			return;
		}
		beans.removeIf(bean -> {
			if (mockedTypes.contains(bean.qualifiedName)) {
				return true;
			}
			for (String iface : bean.interfaceNames) {
				if (mockedTypes.contains(iface)) {
					return true;
				}
			}
			return false;
		});
	}

	// ── Collect @ConditionalOnBean requirements ───────────────────

	private Map<String, String> collectConditionalRequirements(List<BeanDefinition> beans) {
		Map<String, String> requiredTypes = new HashMap<>();
		for (BeanDefinition bean : beans) {
			if (bean.conditionalOnBeanType != null) {
				requiredTypes.put(bean.qualifiedName, bean.conditionalOnBeanType);
			}
			if (bean.methodConditionalOnBeanType != null) {
				requiredTypes.put(bean.qualifiedName, bean.methodConditionalOnBeanType);
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
		// First pass: log method-level @Replaces from pre-populated field
		for (BeanDefinition bean : beans) {
			if (!bean.isFactoryMethod())
				continue;
			if (bean.methodLevelReplaces != null) {
				log.debug("[Summer] Method-level @Replaces: {}.{} replaces {}", bean.configClassName,
						bean.producerMethodName, bean.methodLevelReplaces);
			}
		}

		// Second pass: collect all replaced beans
		List<BeanDefinition> replaced = new ArrayList<>();
		for (BeanDefinition bean : beans) {
			// Class-level @Replaces
			if (bean.replacesTargetClass != null) {
				BeanDefinition target = findBeanByName(beans, bean.replacesTargetClass);
				if (target == null)
					throw new NoSuchBeanException("@Replaces target not found: " + bean.replacesTargetClass);
				log.debug("[Summer] Class-level @Replaces: {} replaces {}", bean.qualifiedName,
						bean.replacesTargetClass);
				replaced.add(target);
			}
			// Method-level @Replaces
			if (bean.methodLevelReplaces != null) {
				BeanDefinition target = findBeanByReturnType(beans, bean.methodLevelReplaces, bean);
				if (target == null)
					throw new NoSuchBeanException("@Replaces target not found: " + bean.methodLevelReplaces);
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
