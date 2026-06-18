package summer.aot.generator;

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
import summer.core.BeanCondition;
import summer.core.BeanDescriptorResolver;
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
	 * Full discovery pipeline: discover → enrichment → condition evaluation.
	 */
	public List<BeanDefinition> discover(String packagePrefix) {
		List<BeanDefinition> beans = discoverBeans(packagePrefix);
		new BeanEnrichment(index).enrich(beans);
		resolveConditions(beans);
		return beans;
	}

	/**
	 * Phase 1 only: discover bean classes and factory methods. No enrichment,
	 * no condition evaluation. For module-level AOT where enrichment is done
	 * separately and conditions are evaluated at aggregation time.
	 */
	public List<BeanDefinition> discoverBeans(String packagePrefix) {
		List<BeanDefinition> beans = new ArrayList<>();
		Set<String> collected = new HashSet<>();

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
				// Only allow re-registration if no FactoryBean exists yet
				boolean hasFactoryBean = beans.stream()
						.anyMatch(b -> b instanceof FactoryBean && b.qualifiedName.equals(returnTypeName));
				if (!hasFactoryBean) {
					collected.remove(returnTypeName);
				}
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

	// ── Phase 2: Condition Evaluation ────────────────────────────────

	/**
	 * Evaluate only {@code @Replaces} (class-level and method-level). Does NOT
	 * evaluate {@code @ConditionalOnBean}. For module-level AOT where
	 * cross-module conditions can't be resolved.
	 */
	public void resolveReplaces(List<BeanDefinition> beans) {
		List<BeanCondition> descriptors = buildDescriptors(beans);
		Set<String> typesToRemove = new HashSet<>();
		for (BeanCondition desc : descriptors) {
			if (desc.replaces() != null) {
				if (desc.type().equals(desc.replaces()))
					continue;
				typesToRemove.add(desc.replaces());
			}
		}
		beans.removeIf(b -> typesToRemove.contains(b.qualifiedName));
		removeOrphanedFactoryProducts(beans);
	}

	private void resolveConditions(List<BeanDefinition> beans) {
		// Build descriptors (reads both class-level and method-level @Replaces + @ConditionalOnBean)
		List<BeanCondition> descriptors = buildDescriptors(beans);

		// Unified resolver handles both class-level and method-level @Replaces + @ConditionalOnBean
		BeanDescriptorResolver.Result result = BeanDescriptorResolver.resolve(descriptors);

		beans.removeIf(b -> result.typesToRemove().contains(b.qualifiedName));
		removeOrphanedFactoryProducts(beans);
	}

	private List<BeanCondition> buildDescriptors(List<BeanDefinition> beans) {
		List<BeanCondition> descriptors = new ArrayList<>();
		for (BeanDefinition bean : beans) {
			Set<String> interfaces = new HashSet<>(bean.interfaceNames);

			String conditionalOn = null;
			String replaces = null;

			ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
			if (ci != null) {
				AnnotationInstance condAnn = ci.declaredAnnotation(CONDITIONAL_DOT);
				if (condAnn != null) {
					conditionalOn = condAnn.value().asClass().name().toString();
					bean.conditionalOnTypeName = conditionalOn;
				}

				AnnotationInstance replacesAnn = ci.declaredAnnotation(REPLACES_DOT);
				if (replacesAnn != null) {
					replaces = replacesAnn.value().asClass().name().toString();
				}
			}

			// Also check method-level @ConditionalOnBean and @Replaces for FactoryBeans
			if (bean instanceof FactoryBean fb && fb.configClassName != null) {
				ClassInfo configCi = index.getClassByName(DotName.createSimple(fb.configClassName));
				if (configCi != null) {
					for (MethodInfo method : configCi.methods()) {
						if (!method.name().equals(fb.producerMethodName))
							continue;
						if (conditionalOn == null && method.hasAnnotation(CONDITIONAL_DOT)) {
							conditionalOn = method.annotation(CONDITIONAL_DOT).value().asClass().name().toString();
							bean.conditionalOnTypeName = conditionalOn;
						}
						if (replaces == null && method.hasAnnotation(REPLACES_DOT)) {
							replaces = method.annotation(REPLACES_DOT).value().asClass().name().toString();
							bean.replacesReturnType = replaces;
						}
					}
				}
			}

			descriptors.add(BeanCondition.of(bean.qualifiedName, interfaces, conditionalOn, replaces));
		}
		return descriptors;
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
