package com.github.dropguard.summer.engine;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.exception.BeanCreationException;
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
 *
 * <p>Lives in the engine module (not summer-core) so the foundation layer stays free of Jandex —
 * discovery is engine machinery, not contract.
 */
@Internal
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

    // @WithDefault / @WithName resolution for config-mapping keys happens in the AOT generator
    // (WireMethodGenerator) and the runtime config binder (RuntimeConfigBinder); the discovery
    // phase deliberately does not extract them.

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

        // A @Bean producer for a @ConfigMapping type is an explicit override (the producer wins).
        // The in-loop removal in discoverBeanFactoryMethods was order-dependent — if the mapping's
        // config-properties registration landed after the producer's, both survived and the
        // container failed with a confusing ambiguity. Deduplicate deterministically here, after
        // every class is registered, so the override works regardless of index order.
        removeDuplicateConfigProperties(beans, merged);

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
                throw new BeanCreationException(
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

    /**
     * Deterministic override dedup: when a type has both a discovered {@code @ConfigMapping}
     * config-properties bean and a {@code @Bean} producer for the same type, the producer wins (the
     * mapping's own registration is dropped). Runs after every class is registered, so the result
     * is independent of index iteration order.
     */
    private static void removeDuplicateConfigProperties(
            List<BeanDefinition> beans, IndexView merged) {
        java.util.Map<String, BeanDefinition> producers = new java.util.HashMap<>();
        for (BeanDefinition b : beans) {
            if (b.isFactoryMethod()) {
                producers.put(b.qualifiedName, b);
            }
        }
        if (producers.isEmpty()) {
            return;
        }
        beans.removeIf(
                b ->
                        b instanceof ConfigPropertiesBean cp
                                && (producers.containsKey(cp.qualifiedName)
                                        // A producer returning a concrete implementation (the
                                        // natural
                                        // style) also overrides the synthetic default: the product
                                        // type
                                        // implements the mapping interface. Read from the index
                                        // (not the
                                        // BeanDefinition's interfaceNames, which the BeanEnrichment
                                        // fills
                                        // only later) so the check is order-independent.
                                        || producers.keySet().stream()
                                                .anyMatch(
                                                        name ->
                                                                implementsInterface(
                                                                        merged,
                                                                        name,
                                                                        cp.qualifiedName))));
    }

    /**
     * Whether the type (a producer's {@code @Bean} return type) implements the given interface.
     * Limited to the DIRECT interface declarations available in the index: an interface inherited
     * through a superclass, or a transitive interface whose intermediate ClassInfo is not in the
     * index, is not matched. Both shapes are exotic (a concrete product class typically implements
     * the mapping interface directly); the caller seeds such classes explicitly if needed.
     */
    private static boolean implementsInterface(IndexView index, String typeName, String ifaceName) {
        ClassInfo ci = index.getClassByName(org.jboss.jandex.DotName.createSimple(typeName));
        return ci != null
                && (ci.interfaceNames().contains(org.jboss.jandex.DotName.createSimple(ifaceName))
                        || ci.interfaceNames().stream()
                                .anyMatch(
                                        i -> implementsInterface(index, i.toString(), ifaceName)));
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
        // Class-level @ConditionalOnBean on the PRODUCT type (not the producer method). Discovery
        // is the single place that collects @ConditionalOnBean: the method-level condition above
        // (fillFactoryBean) AND this product-class-level one, both feeding the AND semantics in
        // SharedConditionEvaluator. Reading the product class here keeps the two conditions next
        // to each other instead of re-reading the class in BeanEnrichment.
        if (returnTypeCi != null) {
            AnnotationInstance productConditional =
                    returnTypeCi.annotation(CONDITIONAL_ON_BEAN_DOT);
            if (productConditional != null) {
                fb.conditionalOnBeanType = productConditional.value().asClass().name().toString();
            }
        }
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
            fb.addParameter(
                    JandexTypes.paramTypeName(
                            method.parameterType(i), configCi.name() + "#" + method.name()));
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
