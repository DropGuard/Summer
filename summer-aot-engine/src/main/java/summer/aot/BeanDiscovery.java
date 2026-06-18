package summer.aot;

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
 * <li>Discover beans — classes
 * (@Component, @Configuration, @ConfigurationProperties, meta-annotations) and
 * factory methods (@Bean in @Configuration classes) in a single pass</li>
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
	private static final DotName DEFAULT_VALUE_DOT = DotName.createSimple("summer.core.config.DefaultValue");

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

		// Phase 1: Discover beans (classes + factory methods in one pass)
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() || !matchesPackage(ci, packagePrefix))
				continue;
			if (ci.isInterface() || ci.isAbstract()) {
				if (hasMetaComponentAnnotation(ci, new HashSet<>())) {
					throw new summer.core.exception.BeanCreationException(summer.core.ErrorCode.BEAN_CREATION_FAILED,
							"@Component cannot be placed on an interface or abstract class: " + ci.name()
									+ ". Annotate the concrete implementation instead.");
				}
				continue;
			}
			boolean isNew = !collected.contains(ci.name().toString());
			discoverClass(ci, beans, collected);
			if (isNew && ci.hasAnnotation(CONFIG_DOT)) {
				discoverBeanFactoryMethods(ci, beans, collected);
			}
		}

		// Phase 2: Evaluate conditions
		resolveConditions(beans);

		// Phase 3: Enrich remaining metadata
		new BeanEnrichment(index).enrich(beans);

			return beans;
		}

		/**
		 * Scoped discovery: only processes classes whose names are in the
		 * given closure set. Used by {@link TestGraphGenerator} to build a
		 * minimal BeanDefinition list for a test graph.
		 */
		public List<BeanDefinition> discoverScoped(Set<String> closureNames) {
			List<BeanDefinition> beans = new ArrayList<>();
			Set<String> collected = new HashSet<>();

			for (String name : closureNames) {
				ClassInfo ci = index.getClassByName(DotName.createSimple(name));
				if (ci == null || ci.isAnnotation()) {
					continue;
				}
				if (ci.isInterface() || ci.isAbstract()) {
					if (hasMetaComponentAnnotation(ci, new HashSet<>())) {
						throw new summer.core.exception.BeanCreationException(
								summer.core.ErrorCode.BEAN_CREATION_FAILED,
								"@Component cannot be placed on an interface or abstract class: "
										+ ci.name()
										+ ". Annotate the concrete implementation instead.");
					}
					continue;
				}
				discoverClass(ci, beans, collected);
				if (ci.hasAnnotation(CONFIG_DOT)) {
					discoverBeanFactoryMethods(ci, beans, collected);
				}
			}

			resolveConditions(beans);
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
				bean.configPropertiesPrefix = (ann != null && ann.value("prefix") != null)
						? ann.value("prefix").asString()
						: "";
				extractDefaultValues(ci, bean);
				beans.add(bean);
			}
		} else if (ci.hasAnnotation(COMPONENT_DOT) || ci.hasAnnotation(CONFIG_DOT)
				|| hasMetaComponentAnnotation(ci, new HashSet<>())) {
			if (collected.add(name))
				beans.add(new ComponentBean(name, ci.simpleName()));
		}
	}

	/**
	 * Extracts {@code @DefaultValue} annotations from record components.
	 */
	private void extractDefaultValues(ClassInfo ci, ConfigPropertiesBean bean) {
		List<org.jboss.jandex.RecordComponentInfo> components = ci.recordComponents();
		if (components == null || components.isEmpty())
			return;

		for (org.jboss.jandex.RecordComponentInfo comp : components) {
			String fieldName = comp.name();
			AnnotationInstance defaultAnn = comp.annotation(DEFAULT_VALUE_DOT);
			if (defaultAnn != null) {
				String rawValue = defaultAnn.value().asString();
				bean.defaultValues.put(fieldName, rawValue);
				bean.fieldTypes.put(fieldName, comp.type().name().toString());
			}
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

	private void discoverBeanFactoryMethods(ClassInfo configCi, List<BeanDefinition> beans, Set<String> collected) {
		for (MethodInfo method : configCi.methods()) {
			if (!method.hasAnnotation(BEAN_DOT))
				continue;

			org.jboss.jandex.Type returnType = method.returnType();
			if (returnType == null)
				continue;

			String returnTypeName = returnType.name().toString();
			boolean hasReplaces = method.hasAnnotation(REPLACES_DOT);

			// A non-replacing @Bean takes priority over @ConfigurationProperties
			if (!hasReplaces) {
				beans.removeIf(b -> b instanceof ConfigPropertiesBean && b.qualifiedName.equals(returnTypeName));
				collected.remove(returnTypeName);
			}

			if (collected.add(returnTypeName)) {
				// First @Bean for this type — register it
				beans.add(createFactoryBean(returnTypeName, configCi, method));
			} else if (hasReplaces) {
				// @Replaces — override the existing @Bean's producer
				BeanDefinition existing = findBeanByClass(beans, returnTypeName, FactoryBean.class);
				if (existing instanceof FactoryBean fb) {
					fillFactoryBean(fb, configCi, method);
				}
			}
		}
	}

	private FactoryBean createFactoryBean(String returnTypeName, ClassInfo configCi, MethodInfo method) {
		FactoryBean fb = new FactoryBean(returnTypeName, method.returnType().name().withoutPackagePrefix());
		fillFactoryBean(fb, configCi, method);
		return fb;
	}

	private void fillFactoryBean(FactoryBean fb, ClassInfo configCi, MethodInfo method) {
		fb.configClassName = configCi.name().toString();
		fb.producerMethodName = method.name();
		fb.producerParamTypes.clear();
		for (int i = 0; i < method.parametersCount(); i++) {
			fb.producerParamTypes.add(method.parameterType(i).name().toString());
		}
	}

	// ── Phase 2: Condition Evaluation (Three-Phase) ──────────────────

	private void resolveConditions(List<BeanDefinition> beans) {
		Map<String, String> requiredTypes = collectConditionalRequirements(beans);
		List<BeanDefinition> topoOrder = buildTopologicalOrder(beans, requiredTypes);
		resolveReplaces(beans);
		resolveConditionalOnBean(beans, topoOrder, requiredTypes);
		removeOrphanedFactoryProducts(beans);
	}

	// ── Dependency Graph + Topological Sort ──────────────────────────

	/**
	 * Builds a map from bean qualified name → the type it @ConditionalOnBean
	 * requires. Used by both topological sort and condition evaluation.
	 */
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
		return requiredTypes;
	}

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

	/**
	 * Resolves all @Replaces: class-level (annotation on the replacement) and
	 * method-level (annotation on @Bean methods). Removes replaced beans.
	 */
	private void resolveReplaces(List<BeanDefinition> beans) {
		// Mark method-level @Replaces targets first
		for (BeanDefinition bean : beans) {
			if (!(bean instanceof FactoryBean fb) || fb.configClassName == null)
				continue;
			String target = resolveMethodLevelReplaces(fb);
			if (target != null) {
				bean.replacesReturnType = target;
			}
		}

		// Collect all beans to remove
		List<BeanDefinition> replaced = new ArrayList<>();
		for (BeanDefinition bean : beans) {
			// Class-level: @Replaces annotation on the bean itself
			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci != null) {
				AnnotationInstance ann = ci.annotation(REPLACES_DOT);
				if (ann != null) {
					String targetName = ann.value().asClass().name().toString();
					BeanDefinition target = findBeanByName(beans, targetName);
					if (target == null)
						throw new NoSuchBeanException("@Replaces target not found: " + targetName);
					replaced.add(target);
				}
			}
			// Method-level: @Replaces annotation on @Bean method
			if (bean.replacesReturnType != null) {
				BeanDefinition target = findBeanByReturnType(beans, bean.replacesReturnType, bean);
				if (target == null)
					throw new NoSuchBeanException("@Replaces target not found: " + bean.replacesReturnType);
				replaced.add(target);
			}
		}

		beans.removeAll(replaced);
	}

	/**
	 * Returns the target type name if the @Bean method has @Replaces, null
	 * otherwise.
	 */
	private String resolveMethodLevelReplaces(FactoryBean fb) {
		ClassInfo configCi = index.getClassByName(DotName.createSimple(fb.configClassName));
		if (configCi == null)
			return null;

		for (MethodInfo method : configCi.methods()) {
			if (!method.name().equals(fb.producerMethodName) || !method.hasAnnotation(REPLACES_DOT))
				continue;
			AnnotationInstance ann = method.annotation(REPLACES_DOT);
			if (ann != null) {
				return ann.value().asClass().name().toString();
			}
		}
		return null;
	}

	// ── @ConditionalOnBean (Linear Pass) ─────────────────────────────

	private void resolveConditionalOnBean(List<BeanDefinition> beans, List<BeanDefinition> topoOrder,
			Map<String, String> requiredTypes) {
		Set<String> available = new HashSet<>();
		for (BeanDefinition bean : beans) {
			available.add(bean.qualifiedName);
			if (bean instanceof ComponentBean cb) {
				available.addAll(cb.interfaceNames);
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
