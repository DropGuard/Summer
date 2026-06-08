package summer.plugin;

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
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.NoSuchBeanException;

/**
 * Discovers beans from a Jandex index and evaluates conditions.
 *
 * <p>
 * Pipeline:
 * </p>
 * <ol>
 * <li>Discover class-level beans
 * (@Component, @Configuration, @ConfigurationProperties, meta-annotations)</li>
 * <li>Discover factory method beans (@Bean in @Configuration classes)</li>
 * <li>Evaluate conditions (@ConditionalOnBean, @Replaces) and remove
 * unsatisfied beans</li>
 * <li>Enrich via {@link BeanEnrichment}</li>
 * </ol>
 */
public final class BeanDiscovery {

	private static final DotName COMPONENT_DOT = DotName.createSimple("summer.core.Component");
	private static final DotName CONFIG_DOT = DotName.createSimple("summer.core.annotation.Configuration");
	private static final DotName BEAN_DOT = DotName.createSimple("summer.core.annotation.Bean");
	private static final DotName CONFIG_PROPERTIES_DOT = DotName
			.createSimple("summer.core.config.ConfigurationProperties");
	private static final DotName REPLACES_DOT = DotName.createSimple("summer.core.annotation.Replaces");
	private static final DotName CONDITIONAL_DOT = DotName.createSimple("summer.core.annotation.ConditionalOnBean");

	private final IndexView index;

	public BeanDiscovery(IndexView index) {
		this.index = index;
	}

	/**
	 * Full discovery pipeline: discover → condition evaluation → enrichment.
	 */
	public List<BeanDefinition> discover(String packagePrefix) {
		List<BeanDefinition> beans = new ArrayList<>();
		Set<String> collected = new HashSet<>();

		// Phase 1: Discover beans
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() || !matchesPackage(ci, packagePrefix))
				continue;
			discoverClass(ci, beans, collected);
		}
		discoverBeanFactoryMethods(beans, collected);

		// Phase 2: Prepare and evaluate conditions
		scanMethodLevelReplaces(beans);
		resolveConditions(beans);

		// Phase 3: Enrich remaining metadata
		new BeanEnrichment(index).enrich(beans);

		return beans;
	}

	// ── Phase 1: Discovery ────────────────────────────────────────────

	private void discoverClass(ClassInfo ci, List<BeanDefinition> beans, Set<String> collected) {
		String name = ci.name().toString();

		if (ci.hasAnnotation(CONFIG_PROPERTIES_DOT)) {
			if (collected.add(name)) {
				ConfigPropertiesBean bean = new ConfigPropertiesBean(name, ci.simpleName());
				AnnotationInstance ann = ci.annotation(CONFIG_PROPERTIES_DOT);
				bean.configPropertiesPrefix = (ann != null && ann.value() != null) ? ann.value().asString() : "";
				beans.add(bean);
			}
		} else if (ci.hasAnnotation(COMPONENT_DOT) || ci.hasAnnotation(CONFIG_DOT)
				|| hasMetaComponentAnnotation(ci, new HashSet<>())) {
			if (collected.add(name))
				beans.add(new ComponentBean(name, ci.simpleName()));
		}
	}

	private boolean hasMetaComponentAnnotation(ClassInfo classInfo, Set<DotName> visited) {
		if (classInfo == null)
			return false;
		if (!visited.add(classInfo.name()))
			return false;
		if (classInfo.hasAnnotation(COMPONENT_DOT))
			return true;
		for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
			if (hasMetaComponentAnnotation(index.getClassByName(ann.name()), visited))
				return true;
		}
		return false;
	}

	private void discoverBeanFactoryMethods(List<BeanDefinition> beans, Set<String> collected) {
		for (ClassInfo configCi : index.getKnownClasses()) {
			if (configCi.isAnnotation() || configCi.isInterface() || configCi.isAbstract())
				continue;
			if (!configCi.hasAnnotation(CONFIG_DOT))
				continue;

			for (MethodInfo method : configCi.methods()) {
				if (!method.hasAnnotation(BEAN_DOT))
					continue;

				org.jboss.jandex.Type returnType = method.returnType();
				if (returnType == null)
					continue;

				String returnTypeName = returnType.name().toString();
				boolean hasReplaces = method.hasAnnotation(REPLACES_DOT);

				if (!hasReplaces) {
					BeanDefinition existing = findBeanByClass(beans, returnTypeName, ConfigPropertiesBean.class);
					if (existing != null) {
						beans.remove(existing);
						collected.remove(returnTypeName);
					}
				}

				if (collected.add(returnTypeName)) {
					FactoryBean factoryBean = new FactoryBean(returnTypeName, returnType.name().withoutPackagePrefix());
					factoryBean.configClassName = configCi.name().toString();
					factoryBean.producerMethodName = method.name();
					for (int i = 0; i < method.parametersCount(); i++) {
						factoryBean.producerParamTypes.add(method.parameterType(i).name().toString());
					}
					beans.add(factoryBean);
				} else if (hasReplaces) {
					BeanDefinition existing = findBeanByClass(beans, returnTypeName, FactoryBean.class);
					if (existing instanceof FactoryBean fb) {
						fb.configClassName = configCi.name().toString();
						fb.producerMethodName = method.name();
						fb.producerParamTypes.clear();
						for (int i = 0; i < method.parametersCount(); i++) {
							fb.producerParamTypes.add(method.parameterType(i).name().toString());
						}
					}
				}
			}
		}
	}

	// ── Phase 2: Condition Evaluation (Three-Phase) ──────────────────

	private void resolveConditions(List<BeanDefinition> beans) {
		List<BeanDefinition> topoOrder = buildTopologicalOrder(beans);
		resolveReplaces(beans);
		resolveConditionalOnBean(beans, topoOrder);
		removeOrphanedFactoryProducts(beans);
	}

	// ── Dependency Graph + Topological Sort ──────────────────────────

	private List<BeanDefinition> buildTopologicalOrder(List<BeanDefinition> beans) {
		Map<String, String> requiredTypes = new HashMap<>();
		for (BeanDefinition bean : beans) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;

			AnnotationInstance condAnn = ci.annotation(CONDITIONAL_DOT);
			if (condAnn != null) {
				requiredTypes.put(bean.qualifiedName, condAnn.value().asClass().name().toString());
			}

			if (bean instanceof FactoryBean fb && fb.configClassName != null) {
				ClassInfo configCi = index.getClassByName(DotName.createSimple(fb.configClassName));
				if (configCi != null) {
					for (MethodInfo method : configCi.methods()) {
						if (method.name().equals(fb.producerMethodName) && method.hasAnnotation(CONDITIONAL_DOT)) {
							requiredTypes.put(bean.qualifiedName,
									method.annotation(CONDITIONAL_DOT).value().asClass().name().toString());
						}
					}
				}
			}
		}

		Map<BeanDefinition, Set<BeanDefinition>> deps = new HashMap<>();
		for (BeanDefinition bean : beans) {
			String required = requiredTypes.get(bean.qualifiedName);
			if (required == null)
				continue;

			Set<BeanDefinition> matches = new HashSet<>();
			for (BeanDefinition other : beans) {
				if (other.qualifiedName.equals(required)) {
					matches.add(other);
				} else if (other instanceof ComponentBean cb && cb.interfaceNames.contains(required)) {
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

	// ── @Replaces ────────────────────────────────────────────────────

	private void scanMethodLevelReplaces(List<BeanDefinition> beans) {
		for (BeanDefinition bean : beans) {
			if (!(bean instanceof FactoryBean fb) || fb.configClassName == null)
				continue;

			ClassInfo configCi = index.getClassByName(DotName.createSimple(fb.configClassName));
			if (configCi == null)
				continue;

			for (MethodInfo method : configCi.methods()) {
				if (!method.name().equals(fb.producerMethodName) || !method.hasAnnotation(REPLACES_DOT))
					continue;

				AnnotationInstance replacesAnn = method.annotation(REPLACES_DOT);
				if (replacesAnn == null)
					continue;

				String targetTypeName = replacesAnn.value().asClass().name().toString();
				if (bean.qualifiedName.equals(targetTypeName))
					continue;

				bean.replacesReturnType = targetTypeName;

				ClassInfo targetCi = index.getClassByName(DotName.createSimple(targetTypeName));
				if (targetCi != null && !targetCi.isInterface()) {
					bean.replacesTargetClass = targetTypeName;
				}
			}
		}
	}

	private void resolveReplaces(List<BeanDefinition> beans) {
		List<BeanDefinition> replaced = new ArrayList<>();

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

	// ── @ConditionalOnBean (Linear Pass) ─────────────────────────────

	private void resolveConditionalOnBean(List<BeanDefinition> beans, List<BeanDefinition> topoOrder) {
		Set<String> available = new HashSet<>();
		for (BeanDefinition bean : beans) {
			available.add(bean.qualifiedName);
			if (bean instanceof ComponentBean cb) {
				available.addAll(cb.interfaceNames);
			}
		}

		Map<String, String> requiredTypes = new HashMap<>();
		for (BeanDefinition bean : beans) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci == null)
				continue;

			AnnotationInstance condAnn = ci.annotation(CONDITIONAL_DOT);
			if (condAnn != null) {
				requiredTypes.put(bean.qualifiedName, condAnn.value().asClass().name().toString());
			}

			if (bean instanceof FactoryBean fb && fb.configClassName != null) {
				ClassInfo configCi = index.getClassByName(DotName.createSimple(fb.configClassName));
				if (configCi != null) {
					for (MethodInfo method : configCi.methods()) {
						if (method.name().equals(fb.producerMethodName) && method.hasAnnotation(CONDITIONAL_DOT)) {
							requiredTypes.put(bean.qualifiedName,
									method.annotation(CONDITIONAL_DOT).value().asClass().name().toString());
						}
					}
				}
			}
		}

		for (BeanDefinition bean : topoOrder) {
			if (!beans.contains(bean))
				continue;

			String required = requiredTypes.get(bean.qualifiedName);
			if (required == null)
				continue;

			if (!available.contains(required)) {
				available.remove(bean.qualifiedName);
				if (bean instanceof ComponentBean cb) {
					available.removeAll(cb.interfaceNames);
				}
				beans.remove(bean);
			}
		}
	}

	private void removeOrphanedFactoryProducts(List<BeanDefinition> beans) {
		Set<String> allBeanNames = new HashSet<>();
		for (BeanDefinition bean : beans) {
			allBeanNames.add(bean.qualifiedName);
		}
		beans.removeIf(b -> b instanceof FactoryBean fb && fb.configClassName != null
				&& !allBeanNames.contains(fb.configClassName));
	}

	// ── Helpers ───────────────────────────────────────────────────────

	private boolean matchesPackage(ClassInfo ci, String packagePrefix) {
		return packagePrefix == null || ci.name().toString().startsWith(packagePrefix);
	}

	private BeanDefinition findBeanByClass(List<BeanDefinition> beans, String qualifiedName, Class<?> type) {
		for (BeanDefinition bean : beans) {
			if (type.isInstance(bean) && bean.qualifiedName.equals(qualifiedName))
				return bean;
		}
		return null;
	}

	private BeanDefinition findBeanByName(List<BeanDefinition> beans, String name) {
		for (BeanDefinition bean : beans) {
			if (bean.qualifiedName.equals(name))
				return bean;
		}
		return null;
	}

	private BeanDefinition findBeanByReturnType(List<BeanDefinition> beans, String returnType,
			BeanDefinition replacement) {
		FactoryBean found = null;
		for (BeanDefinition bean : beans) {
			if (bean == replacement)
				continue;
			if (bean instanceof FactoryBean fb && fb.qualifiedName.equals(returnType)) {
				if (found != null) {
					throw new AmbiguousBeanException("Ambiguous @Replaces: multiple @Bean methods return " + returnType
							+ ": " + found.configClassName + "." + found.producerMethodName + " and "
							+ fb.configClassName + "." + fb.producerMethodName);
				}
				found = fb;
			}
		}
		return found;
	}
}
