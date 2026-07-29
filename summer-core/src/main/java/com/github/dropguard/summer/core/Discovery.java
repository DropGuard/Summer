mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.core;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDeployment;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanEnrichment;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.SharedConditionEvaluator;
mport com.github.dropguard.summer.core.Internal;
import java.util.ArrayList;
mport com.github.dropguard.summer.core.Internal;
import java.util.HashSet;
mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;
import java.util.Set;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.AnnotationInstance;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.ClassInfo;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.DotName;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.IndexView;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.MethodInfo;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Unified bean discovery shared by the Runtime and AOT engines.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This is the single source of truth for "what beans exist in a universe". Both engines feed the
@Internal
mport com.github.dropguard.summer.core.Internal;
 * same {@link BeanDeployment} in and get the same {@link BeanDefinition} candidate list out, so
mport com.github.dropguard.summer.core.Internal;
 * they can never observe divergent candidate sets — parity is enforced by construction, not
mport com.github.dropguard.summer.core.Internal;
 * convention.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Pipeline (discovery + enrichment only — no condition evaluation):
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <ol>
mport com.github.dropguard.summer.core.Internal;
 *   <li>Enumerate component classes ({@code @Component}, {@code @Configuration},
mport com.github.dropguard.summer.core.Internal;
 *       {@code @ConfigMapping}, meta-annotations) and {@code @Bean} factory methods, iterating the
mport com.github.dropguard.summer.core.Internal;
 *       per-module indexes retained by the {@link BeanDeployment} so module boundaries are honoured
mport com.github.dropguard.summer.core.Internal;
 *       natively.
mport com.github.dropguard.summer.core.Internal;
 *   <li>Enrich each definition with constructor params, interfaces, routes, and AOP bindings
mport com.github.dropguard.summer.core.Internal;
 *       (Jandex metadata → {@link BeanDefinition} fields).
mport com.github.dropguard.summer.core.Internal;
 * </ol>
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Condition evaluation ({@code @ConditionalOnBean}/{@Replaces}) and mock removal are
mport com.github.dropguard.summer.core.Internal;
 * deliberately <em>not</em> part of discovery: they depend on the test's {@code @Mock} set and
mport com.github.dropguard.summer.core.Internal;
 * therefore run later, in each engine's {@code build} method, via {@link SharedConditionEvaluator}.
mport com.github.dropguard.summer.core.Internal;
 * Keeping them out of discovery means a mock-free discovery result is cached and reused, and the
mport com.github.dropguard.summer.core.Internal;
 * mocked-type removal happens exactly once with the mocks in scope.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class Discovery {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final DotName COMPONENT_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.core.Component");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName CONFIG_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.core.annotation.Configuration");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName BEAN_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.core.annotation.Bean");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName REPLACES_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.core.annotation.Replaces");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName CONDITIONAL_ON_BEAN_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.core.annotation.ConditionalOnBean");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName INTERCEPTOR_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.aop.Interceptor");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName CONFIG_MAPPING_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.core.config.ConfigMapping");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName WITH_DEFAULT_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.core.config.WithDefault");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName WITH_NAME_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.core.config.WithName");
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private Discovery() {}
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Discovers and enriches beans from a {@link BeanDeployment}. Iterates only the per-module
mport com.github.dropguard.summer.core.Internal;
     * indexes the index retains (falling back to the merged index for annotation resolution), so a
mport com.github.dropguard.summer.core.Internal;
     * test universe observes exactly the production-plus-test beans on its classpath — no post-hoc
mport com.github.dropguard.summer.core.Internal;
     * narrowing.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param moduleIndex the module index naming which classes form the universe
mport com.github.dropguard.summer.core.Internal;
     * @return enriched candidate bean definitions (conditions not yet evaluated)
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static List<BeanDefinition> discover(BeanDeployment moduleIndex) {
mport com.github.dropguard.summer.core.Internal;
        IndexView merged = moduleIndex.discoveryIndex();
mport com.github.dropguard.summer.core.Internal;
        List<BeanDefinition> beans = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
        Set<String> collected = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        for (String mod : moduleIndex.archives()) {
mport com.github.dropguard.summer.core.Internal;
            IndexView modIdx = moduleIndex.archiveIndex(mod);
mport com.github.dropguard.summer.core.Internal;
            for (ClassInfo ci : modIdx.getKnownClasses()) {
mport com.github.dropguard.summer.core.Internal;
                registerClass(ci, beans, collected, merged, moduleIndex);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        new BeanEnrichment(merged).enrich(beans);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        // Merge engine-provided (synthetic) beans into the candidate set. This is the
mport com.github.dropguard.summer.core.Internal;
        // single convergence point (Quarkus' beansView): scanned beans + synthetic
mport com.github.dropguard.summer.core.Internal;
        // beans flow through one list, so both engines observe the same candidates.
mport com.github.dropguard.summer.core.Internal;
        List<BeanDefinition> beansView = new ArrayList<>(beans);
mport com.github.dropguard.summer.core.Internal;
        beansView.addAll(moduleIndex.syntheticBeans());
mport com.github.dropguard.summer.core.Internal;
        return beansView;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── Phase 1: Discovery ────────────────────────────────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Registers the single bean (if any) defined by a class: a {@code @ConfigMapping} bean, or a
mport com.github.dropguard.summer.core.Internal;
     * {@code @Component}/{@code @Configuration}/meta-component bean plus its {@code @Bean} factory
mport com.github.dropguard.summer.core.Internal;
     * methods. Returns early for annotations, abstract/interface types (after rejecting
mport com.github.dropguard.summer.core.Internal;
     * meta-annotated ones), and already-collected types.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    private static void registerClass(
mport com.github.dropguard.summer.core.Internal;
            ClassInfo ci,
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> beans,
mport com.github.dropguard.summer.core.Internal;
            Set<String> collected,
mport com.github.dropguard.summer.core.Internal;
            IndexView merged,
mport com.github.dropguard.summer.core.Internal;
            BeanDeployment moduleIndex) {
mport com.github.dropguard.summer.core.Internal;
        if (ci.isAnnotation()) return;
mport com.github.dropguard.summer.core.Internal;
        if (ci.isInterface() || ci.isAbstract()) {
mport com.github.dropguard.summer.core.Internal;
            if (hasMetaComponentAnnotation(ci, merged, new HashSet<DotName>())) {
mport com.github.dropguard.summer.core.Internal;
                throw new com.github.dropguard.summer.core.exception.BeanCreationException(
mport com.github.dropguard.summer.core.Internal;
                        "@Component cannot be placed on an interface or abstract class: "
mport com.github.dropguard.summer.core.Internal;
                                + ci.name()
mport com.github.dropguard.summer.core.Internal;
                                + ". Annotate the concrete implementation instead.");
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            // A @ConfigMapping interface is a valid config bean — it has no constructor to
mport com.github.dropguard.summer.core.Internal;
            // scan and is bound (not instantiated) by ConfigBinder/RuntimeBeanAdapter, so it
mport com.github.dropguard.summer.core.Internal;
            // must reach registerConfigProperties below. Every other interface/abstract type
mport com.github.dropguard.summer.core.Internal;
            // is skipped.
mport com.github.dropguard.summer.core.Internal;
            if (!ci.hasAnnotation(CONFIG_MAPPING_DOT)) {
mport com.github.dropguard.summer.core.Internal;
                return;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (!collected.add(ci.name().toString())) return;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        if (isConfigurationProperties(ci)) {
mport com.github.dropguard.summer.core.Internal;
            registerConfigProperties(ci, beans, merged, moduleIndex);
mport com.github.dropguard.summer.core.Internal;
        } else if (isComponentLike(ci, merged)) {
mport com.github.dropguard.summer.core.Internal;
            registerComponent(ci, beans, merged, moduleIndex);
mport com.github.dropguard.summer.core.Internal;
            if (ci.hasAnnotation(CONFIG_DOT))
mport com.github.dropguard.summer.core.Internal;
                discoverBeanFactoryMethods(ci, beans, merged, moduleIndex);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static boolean isConfigurationProperties(ClassInfo ci) {
mport com.github.dropguard.summer.core.Internal;
        return ci.hasAnnotation(CONFIG_MAPPING_DOT);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static boolean isComponentLike(ClassInfo ci, IndexView merged) {
mport com.github.dropguard.summer.core.Internal;
        return ci.hasAnnotation(COMPONENT_DOT)
mport com.github.dropguard.summer.core.Internal;
                || ci.hasAnnotation(CONFIG_DOT)
mport com.github.dropguard.summer.core.Internal;
                || hasMetaComponentAnnotation(ci, merged, new HashSet<DotName>());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static void registerConfigProperties(
mport com.github.dropguard.summer.core.Internal;
            ClassInfo ci,
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> beans,
mport com.github.dropguard.summer.core.Internal;
            IndexView merged,
mport com.github.dropguard.summer.core.Internal;
            BeanDeployment moduleIndex) {
mport com.github.dropguard.summer.core.Internal;
        ConfigPropertiesBean bean = new ConfigPropertiesBean(ci.name().toString(), ci.simpleName());
mport com.github.dropguard.summer.core.Internal;
        bean.archiveName = moduleIndex.archiveOf(ci.name().toString());
mport com.github.dropguard.summer.core.Internal;
        AnnotationInstance ann = ci.annotation(CONFIG_MAPPING_DOT);
mport com.github.dropguard.summer.core.Internal;
        bean.configPropertiesPrefix =
mport com.github.dropguard.summer.core.Internal;
                (ann != null && ann.value("prefix") != null) ? ann.value("prefix").asString() : "";
mport com.github.dropguard.summer.core.Internal;
        extractDefaultValues(ci, bean, merged);
mport com.github.dropguard.summer.core.Internal;
        beans.add(bean);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static void registerComponent(
mport com.github.dropguard.summer.core.Internal;
            ClassInfo ci,
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> beans,
mport com.github.dropguard.summer.core.Internal;
            IndexView merged,
mport com.github.dropguard.summer.core.Internal;
            BeanDeployment moduleIndex) {
mport com.github.dropguard.summer.core.Internal;
        BeanDefinition bean = createBaseDefinition(ci, merged, moduleIndex);
mport com.github.dropguard.summer.core.Internal;
        bean.isInterceptor = ci.annotation(INTERCEPTOR_DOT) != null;
mport com.github.dropguard.summer.core.Internal;
        // declaredAnnotation (not annotation): only @Replaces DIRECTLY on the class
mport com.github.dropguard.summer.core.Internal;
        // counts
mport com.github.dropguard.summer.core.Internal;
        // as class-level. Jandex' annotation() also surfaces a @Replaces declared on a
mport com.github.dropguard.summer.core.Internal;
        // @Bean METHOD, which would wrongly mark the @Configuration class (and its
mport com.github.dropguard.summer.core.Internal;
        // products) as class-level replacers. Reflection-based RuntimeBeanAdapter
mport com.github.dropguard.summer.core.Internal;
        // reads method.getAnnotation() precisely, so declaredAnnotation keeps the
mport com.github.dropguard.summer.core.Internal;
        // two discovery paths consistent.
mport com.github.dropguard.summer.core.Internal;
        AnnotationInstance replacesAnn = ci.declaredAnnotation(REPLACES_DOT);
mport com.github.dropguard.summer.core.Internal;
        if (replacesAnn != null) {
mport com.github.dropguard.summer.core.Internal;
            bean.replacesTargetClass = replacesAnn.value().asClass().name().toString();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        AnnotationInstance condAnn = ci.declaredAnnotation(CONDITIONAL_ON_BEAN_DOT);
mport com.github.dropguard.summer.core.Internal;
        if (condAnn != null) {
mport com.github.dropguard.summer.core.Internal;
            bean.conditionalOnBeanType = condAnn.value().asClass().name().toString();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        beans.add(bean);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final short ABSTRACT_METHOD = 0x0400; // java.lang.reflect.Modifier.ABSTRACT
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static void extractDefaultValues(
mport com.github.dropguard.summer.core.Internal;
            ClassInfo ci, ConfigPropertiesBean bean, IndexView merged) {
mport com.github.dropguard.summer.core.Internal;
        // Interface model (Quarkus-style @ConfigMapping): abstract methods are the
mport com.github.dropguard.summer.core.Internal;
        // config keys; @WithDefault supplies defaults, @WithName overrides the key.
mport com.github.dropguard.summer.core.Internal;
        if (ci.isInterface()) {
mport com.github.dropguard.summer.core.Internal;
            for (org.jboss.jandex.MethodInfo method : ci.methods()) {
mport com.github.dropguard.summer.core.Internal;
                if ((method.flags() & ABSTRACT_METHOD) == 0) {
mport com.github.dropguard.summer.core.Internal;
                    continue;
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
                String fieldName = resolveKeyName(method);
mport com.github.dropguard.summer.core.Internal;
                AnnotationInstance defaultAnn = method.annotation(WITH_DEFAULT_DOT);
mport com.github.dropguard.summer.core.Internal;
                if (defaultAnn != null) {
mport com.github.dropguard.summer.core.Internal;
                    String rawValue = defaultAnn.value().asString();
mport com.github.dropguard.summer.core.Internal;
                    bean.defaultValues.put(fieldName, rawValue);
mport com.github.dropguard.summer.core.Internal;
                    bean.fieldTypes.put(fieldName, method.returnType().name().toString());
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            return;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * The resolved key for a config mapping method: {@code @WithName} value (camelCased) if
mport com.github.dropguard.summer.core.Internal;
     * present, else the camelCased method name. Must match {@code
mport com.github.dropguard.summer.core.Internal;
     * ConfigBinder.ConfigMappingHandler.resolveKey}.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    private static String resolveKeyName(org.jboss.jandex.MethodInfo method) {
mport com.github.dropguard.summer.core.Internal;
        AnnotationInstance withName = method.annotation(WITH_NAME_DOT);
mport com.github.dropguard.summer.core.Internal;
        if (withName != null
mport com.github.dropguard.summer.core.Internal;
                && withName.value("value") != null
mport com.github.dropguard.summer.core.Internal;
                && !withName.value("value").asString().isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            return normalizeKey(withName.value("value").asString());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return normalizeKey(method.name());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static String normalizeKey(String key) {
mport com.github.dropguard.summer.core.Internal;
        // Mirrors ConfigBinder.toCamelCase semantics for simple (non-nested) keys.
mport com.github.dropguard.summer.core.Internal;
        String[] parts = key.split("[-_.]");
mport com.github.dropguard.summer.core.Internal;
        if (parts.length == 1) return key;
mport com.github.dropguard.summer.core.Internal;
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
mport com.github.dropguard.summer.core.Internal;
        for (int i = 1; i < parts.length; i++) {
mport com.github.dropguard.summer.core.Internal;
            if (!parts[i].isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
                sb.append(Character.toUpperCase(parts[i].charAt(0)))
mport com.github.dropguard.summer.core.Internal;
                        .append(parts[i].substring(1).toLowerCase());
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return sb.toString();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static boolean hasMetaComponentAnnotation(
mport com.github.dropguard.summer.core.Internal;
            ClassInfo classInfo, IndexView index, Set<DotName> visited) {
mport com.github.dropguard.summer.core.Internal;
        if (classInfo == null) return false;
mport com.github.dropguard.summer.core.Internal;
        if (!visited.add(classInfo.name())) return false;
mport com.github.dropguard.summer.core.Internal;
        if (classInfo.hasAnnotation(COMPONENT_DOT)) return true;
mport com.github.dropguard.summer.core.Internal;
        for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
mport com.github.dropguard.summer.core.Internal;
            if (hasMetaComponentAnnotation(index.getClassByName(ann.name()), index, visited))
mport com.github.dropguard.summer.core.Internal;
                return true;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return false;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static void discoverBeanFactoryMethods(
mport com.github.dropguard.summer.core.Internal;
            ClassInfo configCi,
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> beans,
mport com.github.dropguard.summer.core.Internal;
            IndexView merged,
mport com.github.dropguard.summer.core.Internal;
            BeanDeployment moduleIndex) {
mport com.github.dropguard.summer.core.Internal;
        for (MethodInfo method : configCi.methods()) {
mport com.github.dropguard.summer.core.Internal;
            if (!method.hasAnnotation(BEAN_DOT)) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            org.jboss.jandex.Type returnType = method.returnType();
mport com.github.dropguard.summer.core.Internal;
            if (returnType == null) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            String returnTypeName = returnType.name().toString();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            // A @Bean takes priority over a discovered @ConfigMapping
mport com.github.dropguard.summer.core.Internal;
            beans.removeIf(
mport com.github.dropguard.summer.core.Internal;
                    b ->
mport com.github.dropguard.summer.core.Internal;
                            b instanceof ConfigPropertiesBean
mport com.github.dropguard.summer.core.Internal;
                                    && b.qualifiedName.equals(returnTypeName));
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            beans.add(createFactoryBean(returnTypeName, configCi, method, merged, moduleIndex));
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static BeanDefinition createBaseDefinition(
mport com.github.dropguard.summer.core.Internal;
            ClassInfo ci, IndexView merged, BeanDeployment moduleIndex) {
mport com.github.dropguard.summer.core.Internal;
        BeanDefinition bean = new BeanDefinition(ci.name().toString(), ci.simpleName());
mport com.github.dropguard.summer.core.Internal;
        bean.archiveName = moduleIndex.archiveOf(ci.name().toString());
mport com.github.dropguard.summer.core.Internal;
        collectInterfacesRecursive(bean, ci, merged, new HashSet<>());
mport com.github.dropguard.summer.core.Internal;
        return bean;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static BeanDefinition createFactoryBean(
mport com.github.dropguard.summer.core.Internal;
            String returnTypeName,
mport com.github.dropguard.summer.core.Internal;
            ClassInfo configCi,
mport com.github.dropguard.summer.core.Internal;
            MethodInfo method,
mport com.github.dropguard.summer.core.Internal;
            IndexView merged,
mport com.github.dropguard.summer.core.Internal;
            BeanDeployment moduleIndex) {
mport com.github.dropguard.summer.core.Internal;
        ClassInfo returnTypeCi = merged.getClassByName(method.returnType().name());
mport com.github.dropguard.summer.core.Internal;
        BeanDefinition fb;
mport com.github.dropguard.summer.core.Internal;
        if (returnTypeCi != null) {
mport com.github.dropguard.summer.core.Internal;
            fb = createBaseDefinition(returnTypeCi, merged, moduleIndex);
mport com.github.dropguard.summer.core.Internal;
        } else {
mport com.github.dropguard.summer.core.Internal;
            fb =
mport com.github.dropguard.summer.core.Internal;
                    new BeanDefinition(
mport com.github.dropguard.summer.core.Internal;
                            returnTypeName, method.returnType().name().withoutPackagePrefix());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        // A @Bean product belongs to its declaring @Configuration's archive.
mport com.github.dropguard.summer.core.Internal;
        fb.archiveName = moduleIndex.archiveOf(configCi.name().toString());
mport com.github.dropguard.summer.core.Internal;
        fillFactoryBean(fb, configCi, method, merged);
mport com.github.dropguard.summer.core.Internal;
        return fb;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static void collectInterfacesRecursive(
mport com.github.dropguard.summer.core.Internal;
            BeanDefinition bean, ClassInfo ci, IndexView merged, Set<String> visited) {
mport com.github.dropguard.summer.core.Internal;
        for (org.jboss.jandex.Type iface : ci.interfaceTypes()) {
mport com.github.dropguard.summer.core.Internal;
            String ifaceName = iface.name().toString();
mport com.github.dropguard.summer.core.Internal;
            if (visited.add(ifaceName)) {
mport com.github.dropguard.summer.core.Internal;
                bean.interfaceNames.add(ifaceName);
mport com.github.dropguard.summer.core.Internal;
                ClassInfo ifaceCi = merged.getClassByName(iface.name());
mport com.github.dropguard.summer.core.Internal;
                if (ifaceCi != null) {
mport com.github.dropguard.summer.core.Internal;
                    collectInterfacesRecursive(bean, ifaceCi, merged, visited);
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static void fillFactoryBean(
mport com.github.dropguard.summer.core.Internal;
            BeanDefinition fb, ClassInfo configCi, MethodInfo method, IndexView merged) {
mport com.github.dropguard.summer.core.Internal;
        fb.configClassName = configCi.name().toString();
mport com.github.dropguard.summer.core.Internal;
        fb.producerMethodName = method.name();
mport com.github.dropguard.summer.core.Internal;
        for (int i = 0; i < method.parametersCount(); i++) {
mport com.github.dropguard.summer.core.Internal;
            org.jboss.jandex.Type paramType = method.parameterType(i);
mport com.github.dropguard.summer.core.Internal;
            if (paramType.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE) {
mport com.github.dropguard.summer.core.Internal;
                org.jboss.jandex.ParameterizedType pt = paramType.asParameterizedType();
mport com.github.dropguard.summer.core.Internal;
                if (pt.name().toString().equals("java.util.List") && pt.arguments().size() == 1) {
mport com.github.dropguard.summer.core.Internal;
                    fb.addParameter(
mport com.github.dropguard.summer.core.Internal;
                            "java.util.List<" + pt.arguments().get(0).name().toString() + ">");
mport com.github.dropguard.summer.core.Internal;
                    continue;
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            fb.addParameter(paramType.name().toString());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        AnnotationInstance methodReplaces = method.annotation(REPLACES_DOT);
mport com.github.dropguard.summer.core.Internal;
        if (methodReplaces != null) {
mport com.github.dropguard.summer.core.Internal;
            fb.methodLevelReplaces = methodReplaces.value().asClass().name().toString();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        AnnotationInstance methodConditional = method.annotation(CONDITIONAL_ON_BEAN_DOT);
mport com.github.dropguard.summer.core.Internal;
        if (methodConditional != null) {
mport com.github.dropguard.summer.core.Internal;
            fb.methodConditionalOnBeanType = methodConditional.value().asClass().name().toString();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
