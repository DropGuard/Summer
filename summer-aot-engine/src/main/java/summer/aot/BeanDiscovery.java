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
import summer.core.bean.Scope;

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
		return discover(Scope.classpath());
	}

	public List<BeanDefinition> discover(Scope scope) {
		List<BeanDefinition> beans = new ArrayList<>();
		Set<String> collected = new HashSet<>();

		// Phase 1: Discover beans (classes + factory methods in one pass)
		// AotDiMarker is not @Component — register explicitly for @ConditionalOnBean
		beans.add(new BeanDefinition(summer.core.AotDiMarker.class.getName(), "AotDiMarker"));

		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() || !scope.includes(ci.name().toString()))
				continue;
			if (ci.isInterface() || ci.isAbstract()) {
				if (hasMetaComponentAnnotation(ci, new HashSet<>())) {
					throw new summer.core.exception.BeanCreationException(
							"@Component cannot be placed on an interface or abstract class: " + ci.name()
									+ ". Annotate the concrete implementation instead.");
				}
				continue;
			}
			boolean isNew = !collected.contains(ci.name().toString());
			discoverClass(ci, beans, collected);
			if (isNew && ci.hasAnnotation(CONFIG_DOT)) {
				discoverBeanFactoryMethods(ci, beans);
			}
		}

		// Phase 2: Evaluate conditions
		new summer.core.bean.SharedConditionEvaluator(index).evaluate(beans);

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
				bean.configPropertiesPrefix = (ann != null && ann.value("prefix") != null)
						? ann.value("prefix").asString()
						: "";
				extractDefaultValues(ci, bean);
				beans.add(bean);
			}
		} else if (ci.hasAnnotation(COMPONENT_DOT) || ci.hasAnnotation(CONFIG_DOT)
				|| hasMetaComponentAnnotation(ci, new HashSet<>())) {
			if (collected.add(name)) {
				BeanDefinition bean = createBaseDefinition(ci);
				AnnotationInstance replacesAnn = ci.annotation(REPLACES_DOT);
				if (replacesAnn != null) {
					bean.replacesTargetClass = replacesAnn.value().asClass().name().toString();
				}
				beans.add(bean);
			}
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

	/**
	 * Discovers {@code @Bean} factory methods from a {@code @Configuration} class.
	 *
	 * <p>
	 * Every {@code @Bean} product is added to the bean list unconditionally.
	 * Duplicate return types are resolved downstream by
	 * {@link summer.core.bean.SharedConditionEvaluator} (@Replaces) and
	 * {@link summer.core.bean.SharedDependencyResolver} (unique name validation).
	 * This aligns with the Runtime engine's behavior, where all factory products
	 * are registered first and then filtered.
	 * </p>
	 */
	private void discoverBeanFactoryMethods(ClassInfo configCi, List<BeanDefinition> beans) {
		for (MethodInfo method : configCi.methods()) {
			if (!method.hasAnnotation(BEAN_DOT))
				continue;

			org.jboss.jandex.Type returnType = method.returnType();
			if (returnType == null)
				continue;

			String returnTypeName = returnType.name().toString();

			// A @Bean takes priority over @ConfigurationProperties
			beans.removeIf(b -> b instanceof ConfigPropertiesBean && b.qualifiedName.equals(returnTypeName));

			beans.add(createFactoryBean(returnTypeName, configCi, method));
		}
	}

	private BeanDefinition createBaseDefinition(ClassInfo ci) {
		BeanDefinition bean = new BeanDefinition(ci.name().toString(), ci.simpleName());
		collectInterfacesRecursive(bean, ci, new HashSet<>());
		return bean;
	}

	private BeanDefinition createFactoryBean(String returnTypeName, ClassInfo configCi, MethodInfo method) {
		ClassInfo returnTypeCi = index.getClassByName(method.returnType().name());
		BeanDefinition fb;
		if (returnTypeCi != null) {
			fb = createBaseDefinition(returnTypeCi);
		} else {
			fb = new BeanDefinition(returnTypeName, method.returnType().name().withoutPackagePrefix());
		}
		fillFactoryBean(fb, configCi, method);
		// Propagate class-level @Replaces from config class to factory product
		AnnotationInstance replacesAnn = configCi.annotation(REPLACES_DOT);
		if (replacesAnn != null) {
			fb.replacesTargetClass = replacesAnn.value().asClass().name().toString();
		}
		return fb;
	}

	private void collectInterfacesRecursive(BeanDefinition bean, ClassInfo ci, Set<String> visited) {
		for (org.jboss.jandex.Type iface : ci.interfaceTypes()) {
			String ifaceName = iface.name().toString();
			if (visited.add(ifaceName)) {
				bean.interfaceNames.add(ifaceName);
				ClassInfo ifaceCi = index.getClassByName(iface.name());
				if (ifaceCi != null) {
					collectInterfacesRecursive(bean, ifaceCi, visited);
				}
			}
		}
	}

	private void fillFactoryBean(BeanDefinition fb, ClassInfo configCi, MethodInfo method) {
		fb.configClassName = configCi.name().toString();
		fb.producerMethodName = method.name();
		fb.producerParamTypes.clear();
		for (int i = 0; i < method.parametersCount(); i++) {
			fb.producerParamTypes.add(method.parameterType(i).name().toString());
		}
	}
}
