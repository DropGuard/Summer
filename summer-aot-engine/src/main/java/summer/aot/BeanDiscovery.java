package summer.aot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import summer.core.bean.BeanDefinition;
import summer.core.bean.ConfigPropertiesBean;

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
	public List<BeanDefinition> discover() {
		return discover(null);
	}

	public List<BeanDefinition> discover(String packagePrefix) {
		List<BeanDefinition> beans = new ArrayList<>();
		Set<String> collected = new HashSet<>();

		// Phase 1: Discover beans (classes + factory methods in one pass)
		// AotDiMarker is not @Component — register explicitly for @ConditionalOnBean
		beans.add(new BeanDefinition(summer.core.AotDiMarker.class.getName(), "AotDiMarker"));

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
		new summer.core.bean.SharedConditionEvaluator(index).evaluate(beans);

		// Phase 3: Enrich remaining metadata
		new BeanEnrichment(index).enrich(beans);

		return beans;
	}

	/**
	 * Scoped discovery: only processes classes whose names are in the given closure
	 * set. Used by {@link LocalContextGenerator} to build a minimal BeanDefinition
	 * list for a test graph.
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
					throw new summer.core.exception.BeanCreationException(summer.core.ErrorCode.BEAN_CREATION_FAILED,
							"@Component cannot be placed on an interface or abstract class: " + ci.name()
									+ ". Annotate the concrete implementation instead.");
				}
				continue;
			}
			discoverClass(ci, beans, collected);
			if (ci.hasAnnotation(CONFIG_DOT)) {
				discoverBeanFactoryMethods(ci, beans, collected);
			}
		}

		new summer.core.bean.SharedConditionEvaluator(index).evaluate(beans);
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
				beans.add(new BeanDefinition(name, ci.simpleName()));
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
				BeanDefinition existing = findBeanByClass(beans, returnTypeName);
				if (existing != null && existing.isFactoryMethod()) {
					fillFactoryBean(existing, configCi, method);
				}
			}
		}
	}

	private BeanDefinition createFactoryBean(String returnTypeName, ClassInfo configCi, MethodInfo method) {
		BeanDefinition fb = new BeanDefinition(returnTypeName, method.returnType().name().withoutPackagePrefix());
		fillFactoryBean(fb, configCi, method);
		return fb;
	}

	private void fillFactoryBean(BeanDefinition fb, ClassInfo configCi, MethodInfo method) {
		fb.configClassName = configCi.name().toString();
		fb.producerMethodName = method.name();
		fb.producerParamTypes.clear();
		for (int i = 0; i < method.parametersCount(); i++) {
			fb.producerParamTypes.add(method.parameterType(i).name().toString());
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────

	private boolean matchesPackage(ClassInfo ci, String packagePrefix) {
		return packagePrefix == null || ci.name().toString().startsWith(packagePrefix);
	}

	private BeanDefinition findBeanByClass(List<BeanDefinition> beans, String qualifiedName) {
		for (BeanDefinition bean : beans) {
			if (bean.qualifiedName.equals(qualifiedName))
				return bean;
		}
		return null;
	}
}
