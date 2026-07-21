package summer.core;

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
import summer.core.bean.BeanDeployment;
import summer.core.bean.BeanEnrichment;
import summer.core.bean.ConfigPropertiesBean;
import summer.core.bean.SharedConditionEvaluator;

/**
 * Unified bean discovery shared by the Runtime and AOT engines.
 *
 * <p>
 * This is the single source of truth for "what beans exist in a universe". Both
 * engines feed the same {@link BeanDeployment} in and get the same
 * {@link BeanDefinition} candidate list out, so they can never observe
 * divergent candidate sets — parity is enforced by construction, not
 * convention.
 * </p>
 *
 * <p>
 * Pipeline (discovery + enrichment only — no condition evaluation):
 * </p>
 * <ol>
 * <li>Enumerate component classes ({@code @Component}, {@code @Configuration},
 * {@code @ConfigurationProperties}, meta-annotations) and {@code @Bean} factory
 * methods, iterating the per-module indexes retained by the
 * {@link BeanDeployment} so module boundaries are honoured natively.</li>
 * <li>Enrich each definition with constructor params, interfaces, routes, and
 * AOP bindings (Jandex metadata → {@link BeanDefinition} fields).</li>
 * </ol>
 *
 * <p>
 * Condition evaluation ({@code @ConditionalOnBean}/{@Replaces}) and mock
 * removal are deliberately <em>not</em> part of discovery: they depend on the
 * test's {@code @Mock} set and therefore run later, in each engine's
 * {@code build} method, via {@link SharedConditionEvaluator}. Keeping them out
 * of discovery means a mock-free discovery result is cached and reused, and the
 * mocked-type removal happens exactly once with the mocks in scope.
 * </p>
 */
public final class Discovery {

	private static final DotName COMPONENT_DOT = DotName.createSimple("summer.core.Component");
	private static final DotName CONFIG_DOT = DotName.createSimple("summer.core.annotation.Configuration");
	private static final DotName BEAN_DOT = DotName.createSimple("summer.core.annotation.Bean");
	private static final DotName CONFIG_PROPERTIES_DOT = DotName
			.createSimple("summer.core.config.ConfigurationProperties");
	private static final DotName REPLACES_DOT = DotName.createSimple("summer.core.annotation.Replaces");
	private static final DotName CONDITIONAL_ON_BEAN_DOT = DotName
			.createSimple("summer.core.annotation.ConditionalOnBean");
	private static final DotName DEFAULT_VALUE_DOT = DotName.createSimple("summer.core.config.DefaultValue");
	private static final DotName INTERCEPTOR_DOT = DotName.createSimple("summer.aop.Interceptor");

	private Discovery() {
	}

	/**
	 * Discovers and enriches beans from a {@link BeanDeployment}. Iterates only the
	 * per-module indexes the index retains (falling back to the merged index for
	 * annotation resolution), so a test universe observes exactly the
	 * production-plus-test beans on its classpath — no post-hoc narrowing.
	 *
	 * @param moduleIndex
	 *            the module index naming which classes form the universe
	 * @return enriched candidate bean definitions (conditions not yet evaluated)
	 */
	public static List<BeanDefinition> discover(BeanDeployment moduleIndex) {
		IndexView merged = moduleIndex.discoveryIndex();
		List<BeanDefinition> beans = new ArrayList<>();
		Set<String> collected = new HashSet<>();

		for (String mod : moduleIndex.archives()) {
			IndexView modIdx = moduleIndex.archiveIndex(mod);
			for (ClassInfo ci : modIdx.getKnownClasses()) {
				registerClass(ci, beans, collected, merged, moduleIndex);
			}
		}

		new BeanEnrichment(merged).enrich(beans);
		return beans;
	}

	// ── Phase 1: Discovery ────────────────────────────────────────────

	/**
	 * Registers the single bean (if any) defined by a class: a
	 * {@code @ConfigurationProperties} bean, or a
	 * {@code @Component}/{@code @Configuration}/meta-component bean plus its
	 * {@code @Bean} factory methods. Returns early for annotations,
	 * abstract/interface types (after rejecting meta-annotated ones), and
	 * already-collected types.
	 */
	private static void registerClass(ClassInfo ci, List<BeanDefinition> beans, Set<String> collected, IndexView merged,
			BeanDeployment moduleIndex) {
		if (ci.isAnnotation())
			return;
		if (ci.isInterface() || ci.isAbstract()) {
			if (hasMetaComponentAnnotation(ci, merged, new HashSet<DotName>())) {
				throw new summer.core.exception.BeanCreationException(
						"@Component cannot be placed on an interface or abstract class: " + ci.name()
								+ ". Annotate the concrete implementation instead.");
			}
			return;
		}
		if (!collected.add(ci.name().toString()))
			return;

		if (isConfigurationProperties(ci)) {
			registerConfigProperties(ci, beans, merged, moduleIndex);
		} else if (isComponentLike(ci, merged)) {
			registerComponent(ci, beans, merged, moduleIndex);
			if (ci.hasAnnotation(CONFIG_DOT))
				discoverBeanFactoryMethods(ci, beans, merged, moduleIndex);
		}
	}

	private static boolean isConfigurationProperties(ClassInfo ci) {
		return ci.hasAnnotation(CONFIG_PROPERTIES_DOT);
	}

	private static boolean isComponentLike(ClassInfo ci, IndexView merged) {
		return ci.hasAnnotation(COMPONENT_DOT) || ci.hasAnnotation(CONFIG_DOT)
				|| hasMetaComponentAnnotation(ci, merged, new HashSet<DotName>());
	}

	private static void registerConfigProperties(ClassInfo ci, List<BeanDefinition> beans, IndexView merged,
			BeanDeployment moduleIndex) {
		ConfigPropertiesBean bean = new ConfigPropertiesBean(ci.name().toString(), ci.simpleName());
		bean.archiveName = moduleIndex.archiveOf(ci.name().toString());
		AnnotationInstance ann = ci.annotation(CONFIG_PROPERTIES_DOT);
		bean.configPropertiesPrefix = (ann != null && ann.value("prefix") != null)
				? ann.value("prefix").asString()
				: "";
		extractDefaultValues(ci, bean, merged);
		beans.add(bean);
	}

	private static void registerComponent(ClassInfo ci, List<BeanDefinition> beans, IndexView merged,
			BeanDeployment moduleIndex) {
		BeanDefinition bean = createBaseDefinition(ci, merged, moduleIndex);
		bean.isInterceptor = ci.annotation(INTERCEPTOR_DOT) != null;
		// declaredAnnotation (not annotation): only @Replaces DIRECTLY on the class
		// counts
		// as class-level. Jandex' annotation() also surfaces a @Replaces declared on a
		// @Bean METHOD, which would wrongly mark the @Configuration class (and its
		// products) as class-level replacers. Reflection-based RuntimeBeanAdapter
		// reads method.getAnnotation() precisely, so declaredAnnotation keeps the
		// two discovery paths consistent.
		AnnotationInstance replacesAnn = ci.declaredAnnotation(REPLACES_DOT);
		if (replacesAnn != null) {
			bean.replacesTargetClass = replacesAnn.value().asClass().name().toString();
		}
		AnnotationInstance condAnn = ci.declaredAnnotation(CONDITIONAL_ON_BEAN_DOT);
		if (condAnn != null) {
			bean.conditionalOnBeanType = condAnn.value().asClass().name().toString();
		}
		beans.add(bean);
	}

	private static void extractDefaultValues(ClassInfo ci, ConfigPropertiesBean bean, IndexView merged) {
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

	private static boolean hasMetaComponentAnnotation(ClassInfo classInfo, IndexView index, Set<DotName> visited) {
		if (classInfo == null)
			return false;
		if (!visited.add(classInfo.name()))
			return false;
		if (classInfo.hasAnnotation(COMPONENT_DOT))
			return true;
		for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
			if (hasMetaComponentAnnotation(index.getClassByName(ann.name()), index, visited))
				return true;
		}
		return false;
	}

	private static void discoverBeanFactoryMethods(ClassInfo configCi, List<BeanDefinition> beans, IndexView merged,
			BeanDeployment moduleIndex) {
		for (MethodInfo method : configCi.methods()) {
			if (!method.hasAnnotation(BEAN_DOT))
				continue;

			org.jboss.jandex.Type returnType = method.returnType();
			if (returnType == null)
				continue;

			String returnTypeName = returnType.name().toString();

			// A @Bean takes priority over @ConfigurationProperties
			beans.removeIf(b -> b instanceof ConfigPropertiesBean && b.qualifiedName.equals(returnTypeName));

			beans.add(createFactoryBean(returnTypeName, configCi, method, merged, moduleIndex));
		}
	}

	private static BeanDefinition createBaseDefinition(ClassInfo ci, IndexView merged, BeanDeployment moduleIndex) {
		BeanDefinition bean = new BeanDefinition(ci.name().toString(), ci.simpleName());
		bean.archiveName = moduleIndex.archiveOf(ci.name().toString());
		collectInterfacesRecursive(bean, ci, merged, new HashSet<>());
		return bean;
	}

	private static BeanDefinition createFactoryBean(String returnTypeName, ClassInfo configCi, MethodInfo method,
			IndexView merged, BeanDeployment moduleIndex) {
		ClassInfo returnTypeCi = merged.getClassByName(method.returnType().name());
		BeanDefinition fb;
		if (returnTypeCi != null) {
			fb = createBaseDefinition(returnTypeCi, merged, moduleIndex);
		} else {
			fb = new BeanDefinition(returnTypeName, method.returnType().name().withoutPackagePrefix());
		}
		// A @Bean product belongs to its declaring @Configuration's archive.
		fb.archiveName = moduleIndex.archiveOf(configCi.name().toString());
		fillFactoryBean(fb, configCi, method, merged);
		return fb;
	}

	private static void collectInterfacesRecursive(BeanDefinition bean, ClassInfo ci, IndexView merged,
			Set<String> visited) {
		for (org.jboss.jandex.Type iface : ci.interfaceTypes()) {
			String ifaceName = iface.name().toString();
			if (visited.add(ifaceName)) {
				bean.interfaceNames.add(ifaceName);
				ClassInfo ifaceCi = merged.getClassByName(iface.name());
				if (ifaceCi != null) {
					collectInterfacesRecursive(bean, ifaceCi, merged, visited);
				}
			}
		}
	}

	private static void fillFactoryBean(BeanDefinition fb, ClassInfo configCi, MethodInfo method, IndexView merged) {
		fb.configClassName = configCi.name().toString();
		fb.producerMethodName = method.name();
		fb.producerParamTypes.clear();
		for (int i = 0; i < method.parametersCount(); i++) {
			org.jboss.jandex.Type paramType = method.parameterType(i);
			fb.producerParamTypes.add(paramType.name().toString());

			if (paramType.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE) {
				org.jboss.jandex.ParameterizedType pt = paramType.asParameterizedType();
				if (pt.name().toString().equals("java.util.List") && pt.arguments().size() == 1) {
					fb.listElementTypes.put(i, pt.arguments().get(0).name().toString());
				}
			}
		}

		AnnotationInstance methodReplaces = method.annotation(REPLACES_DOT);
		if (methodReplaces != null) {
			fb.methodLevelReplaces = methodReplaces.value().asClass().name().toString();
		}

		AnnotationInstance methodConditional = method.annotation(CONDITIONAL_ON_BEAN_DOT);
		if (methodConditional != null) {
			fb.methodConditionalOnBeanType = methodConditional.value().asClass().name().toString();
		}
	}
}
