package com.github.dropguard.summer.core;

import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.BeanDeployment;
import com.github.dropguard.summer.core.bean.BeanEnrichment;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.bean.SharedConditionEvaluator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

/**
 * Unified bean discovery shared by the Runtime and AOT engines.
 *
 * <p>This is the single source of truth for "what beans exist in a universe". Both engines feed the
 * same {@link BeanDeployment} in and get the same {@link BeanDefinition} candidate list out, so
 * they can never observe divergent candidate sets — parity is enforced by construction, not
 * convention.
 *
 * <p>Pipeline (discovery + enrichment only — no condition evaluation):
 *
 * <ol>
 *   <li>Enumerate component classes ({@code @Component}, {@code @Configuration},
 *       {@code @ConfigMapping}, meta-annotations) and {@code @Bean} factory methods, iterating the
 *       per-module indexes retained by the {@link BeanDeployment} so module boundaries are honoured
 *       natively.
 *   <li>Enrich each definition with constructor params, interfaces, routes, and AOP bindings
 *       (Jandex metadata → {@link BeanDefinition} fields).
 * </ol>
 *
 * <p>Condition evaluation ({@code @ConditionalOnBean}/{@Replaces}) and mock removal are
 * deliberately <em>not</em> part of discovery: they depend on the test's {@code @Mock} set and
 * therefore run later, in each engine's {@code build} method, via {@link SharedConditionEvaluator}.
 * Keeping them out of discovery means a mock-free discovery result is cached and reused, and the
 * mocked-type removal happens exactly once with the mocks in scope.
 */
public final class Discovery {

    private static final DotName COMPONENT_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.Component");
    private static final DotName CONFIG_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.annotation.Configuration");
    private static final DotName BEAN_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.annotation.Bean");
    private static final DotName REPLACES_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.annotation.Replaces");
    private static final DotName CONDITIONAL_ON_BEAN_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.annotation.ConditionalOnBean");
    private static final DotName INTERCEPTOR_DOT =
            DotName.createSimple("com.github.dropguard.summer.aop.Interceptor");
    private static final DotName CONFIG_MAPPING_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.config.ConfigMapping");
    private static final DotName WITH_DEFAULT_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.config.WithDefault");
    private static final DotName WITH_NAME_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.config.WithName");

    private Discovery() {}

    /**
     * Discovers and enriches beans from a {@link BeanDeployment}. Iterates only the per-module
     * indexes the index retains (falling back to the merged index for annotation resolution), so a
     * test universe observes exactly the production-plus-test beans on its classpath — no post-hoc
     * narrowing.
     *
     * @param moduleIndex the module index naming which classes form the universe
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

        // Merge engine-provided (synthetic) beans into the candidate set. This is the
        // single convergence point (Quarkus' beansView): scanned beans + synthetic
        // beans flow through one list, so both engines observe the same candidates.
        List<BeanDefinition> beansView = new ArrayList<>(beans);
        beansView.addAll(moduleIndex.syntheticBeans());
        return beansView;
    }

    // ── Phase 1: Discovery ────────────────────────────────────────────

    /**
     * Registers the single bean (if any) defined by a class: a {@code @ConfigMapping} bean, or a
     * {@code @Component}/{@code @Configuration}/meta-component bean plus its {@code @Bean} factory
     * methods. Returns early for annotations, abstract/interface types (after rejecting
     * meta-annotated ones), and already-collected types.
     */
    private static void registerClass(
            ClassInfo ci,
            List<BeanDefinition> beans,
            Set<String> collected,
            IndexView merged,
            BeanDeployment moduleIndex) {
        if (ci.isAnnotation()) return;
        if (ci.isInterface() || ci.isAbstract()) {
            if (hasMetaComponentAnnotation(ci, merged, new HashSet<DotName>())) {
                throw new com.github.dropguard.summer.core.exception.BeanCreationException(
                        "@Component cannot be placed on an interface or abstract class: "
                                + ci.name()
                                + ". Annotate the concrete implementation instead.");
            }
            // A @ConfigMapping interface is a valid config bean — it has no constructor to
            // scan and is bound (not instantiated) by ConfigBinder/RuntimeBeanAdapter, so it
            // must reach registerConfigProperties below. Every other interface/abstract type
            // is skipped.
            if (!ci.hasAnnotation(CONFIG_MAPPING_DOT)) {
                return;
            }
        }
        if (!collected.add(ci.name().toString())) return;

        if (isConfigurationProperties(ci)) {
            registerConfigProperties(ci, beans, merged, moduleIndex);
        } else if (isComponentLike(ci, merged)) {
            registerComponent(ci, beans, merged, moduleIndex);
            if (ci.hasAnnotation(CONFIG_DOT))
                discoverBeanFactoryMethods(ci, beans, merged, moduleIndex);
        }
    }

    private static boolean isConfigurationProperties(ClassInfo ci) {
        return ci.hasAnnotation(CONFIG_MAPPING_DOT);
    }

    private static boolean isComponentLike(ClassInfo ci, IndexView merged) {
        return ci.hasAnnotation(COMPONENT_DOT)
                || ci.hasAnnotation(CONFIG_DOT)
                || hasMetaComponentAnnotation(ci, merged, new HashSet<DotName>());
    }

    private static void registerConfigProperties(
            ClassInfo ci,
            List<BeanDefinition> beans,
            IndexView merged,
            BeanDeployment moduleIndex) {
        ConfigPropertiesBean bean = new ConfigPropertiesBean(ci.name().toString(), ci.simpleName());
        bean.archiveName = moduleIndex.archiveOf(ci.name().toString());
        AnnotationInstance ann = ci.annotation(CONFIG_MAPPING_DOT);
        bean.configPropertiesPrefix =
                (ann != null && ann.value("prefix") != null) ? ann.value("prefix").asString() : "";
        extractDefaultValues(ci, bean, merged);
        beans.add(bean);
    }

    private static void registerComponent(
            ClassInfo ci,
            List<BeanDefinition> beans,
            IndexView merged,
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

    private static final short ABSTRACT_METHOD = 0x0400; // java.lang.reflect.Modifier.ABSTRACT

    private static void extractDefaultValues(
            ClassInfo ci, ConfigPropertiesBean bean, IndexView merged) {
        // Interface model (Quarkus-style @ConfigMapping): abstract methods are the
        // config keys; @WithDefault supplies defaults, @WithName overrides the key.
        if (ci.isInterface()) {
            for (org.jboss.jandex.MethodInfo method : ci.methods()) {
                if ((method.flags() & ABSTRACT_METHOD) == 0) {
                    continue;
                }
                String fieldName = resolveKeyName(method);
                AnnotationInstance defaultAnn = method.annotation(WITH_DEFAULT_DOT);
                if (defaultAnn != null) {
                    String rawValue = defaultAnn.value().asString();
                    bean.defaultValues.put(fieldName, rawValue);
                    bean.fieldTypes.put(fieldName, method.returnType().name().toString());
                }
            }
            return;
        }
    }

    /**
     * The resolved key for a config mapping method: {@code @WithName} value (camelCased) if
     * present, else the camelCased method name. Must match {@code
     * ConfigBinder.ConfigMappingHandler.resolveKey}.
     */
    private static String resolveKeyName(org.jboss.jandex.MethodInfo method) {
        AnnotationInstance withName = method.annotation(WITH_NAME_DOT);
        if (withName != null
                && withName.value("value") != null
                && !withName.value("value").asString().isEmpty()) {
            return normalizeKey(withName.value("value").asString());
        }
        return normalizeKey(method.name());
    }

    private static String normalizeKey(String key) {
        // Mirrors ConfigBinder.toCamelCase semantics for simple (non-nested) keys.
        String[] parts = key.split("[-_.]");
        if (parts.length == 1) return key;
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)))
                        .append(parts[i].substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private static boolean hasMetaComponentAnnotation(
            ClassInfo classInfo, IndexView index, Set<DotName> visited) {
        if (classInfo == null) return false;
        if (!visited.add(classInfo.name())) return false;
        if (classInfo.hasAnnotation(COMPONENT_DOT)) return true;
        for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
            if (hasMetaComponentAnnotation(index.getClassByName(ann.name()), index, visited))
                return true;
        }
        return false;
    }

    private static void discoverBeanFactoryMethods(
            ClassInfo configCi,
            List<BeanDefinition> beans,
            IndexView merged,
            BeanDeployment moduleIndex) {
        for (MethodInfo method : configCi.methods()) {
            if (!method.hasAnnotation(BEAN_DOT)) continue;

            org.jboss.jandex.Type returnType = method.returnType();
            if (returnType == null) continue;

            String returnTypeName = returnType.name().toString();

            // A @Bean takes priority over a discovered @ConfigMapping
            beans.removeIf(
                    b ->
                            b instanceof ConfigPropertiesBean
                                    && b.qualifiedName.equals(returnTypeName));

            beans.add(createFactoryBean(returnTypeName, configCi, method, merged, moduleIndex));
        }
    }

    private static BeanDefinition createBaseDefinition(
            ClassInfo ci, IndexView merged, BeanDeployment moduleIndex) {
        BeanDefinition bean = new BeanDefinition(ci.name().toString(), ci.simpleName());
        bean.archiveName = moduleIndex.archiveOf(ci.name().toString());
        collectInterfacesRecursive(bean, ci, merged, new HashSet<>());
        return bean;
    }

    private static BeanDefinition createFactoryBean(
            String returnTypeName,
            ClassInfo configCi,
            MethodInfo method,
            IndexView merged,
            BeanDeployment moduleIndex) {
        ClassInfo returnTypeCi = merged.getClassByName(method.returnType().name());
        BeanDefinition fb;
        if (returnTypeCi != null) {
            fb = createBaseDefinition(returnTypeCi, merged, moduleIndex);
        } else {
            fb =
                    new BeanDefinition(
                            returnTypeName, method.returnType().name().withoutPackagePrefix());
        }
        // A @Bean product belongs to its declaring @Configuration's archive.
        fb.archiveName = moduleIndex.archiveOf(configCi.name().toString());
        fillFactoryBean(fb, configCi, method, merged);
        return fb;
    }

    private static void collectInterfacesRecursive(
            BeanDefinition bean, ClassInfo ci, IndexView merged, Set<String> visited) {
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

    private static void fillFactoryBean(
            BeanDefinition fb, ClassInfo configCi, MethodInfo method, IndexView merged) {
        fb.configClassName = configCi.name().toString();
        fb.producerMethodName = method.name();
        for (int i = 0; i < method.parametersCount(); i++) {
            org.jboss.jandex.Type paramType = method.parameterType(i);
            if (paramType.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE) {
                org.jboss.jandex.ParameterizedType pt = paramType.asParameterizedType();
                if (pt.name().toString().equals("java.util.List") && pt.arguments().size() == 1) {
                    fb.addParameter(
                            "java.util.List<" + pt.arguments().get(0).name().toString() + ">");
                    continue;
                }
            }
            fb.addParameter(paramType.name().toString());
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
