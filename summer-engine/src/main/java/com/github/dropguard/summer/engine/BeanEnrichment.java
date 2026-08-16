package com.github.dropguard.summer.engine;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.exception.BeanCreationException;
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

/**
 * Enriches discovered bean definitions with constructor params, interface names, condition
 * metadata, and AOP binding information.
 *
 * <p>Route scanning is intentionally absent: web routes are contributed by {@code
 * com.github.dropguard.summer.core.spi.RouteRegistrar} implementations (e.g. {@code summer-web}),
 * keeping this class free of any web-annotation strings.
 *
 * <p>This is the enrichment phase of discovery — runs after the candidate set is enumerated (from a
 * {@link BeanDeployment}) but before condition evaluation ({@code @ConditionalOnBean}/{@Replaces})
 * and dependency resolution. It only reads Jandex metadata into {@link BeanDefinition} fields; it
 * never removes or reorders beans, so it is safe to run on the shared discovery output that both
 * the Runtime and AOT engines consume.
 *
 * <p>Lives in the engine module (not summer-core) so the foundation layer stays free of Jandex —
 * Jandex metadata reading is engine machinery, not contract.
 */
@Internal
public final class BeanEnrichment {

    private static final DotName ORDER_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.annotation.Order");
    private static final DotName INTERCEPTOR_BINDING_DOT =
            DotName.createSimple("com.github.dropguard.summer.aop.InterceptorBinding");
    private static final DotName INTERCEPTOR_DOT =
            DotName.createSimple("com.github.dropguard.summer.aop.Interceptor");
    private static final DotName POST_CONSTRUCT_DOT =
            DotName.createSimple("jakarta.annotation.PostConstruct");

    private final IndexView index;

    public BeanEnrichment(IndexView index) {
        this.index = index;
    }

    /**
     * Enriches beans with constructor params, interface names, condition metadata, and AOP
     * bindings.
     */
    public void enrich(List<BeanDefinition> beans) {
        for (BeanDefinition bean : beans) {
            if (bean instanceof ConfigPropertiesBean) continue;
            ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
            if (ci != null) {
                if (!bean.isFactoryMethod()) {
                    collectConstructorParams(bean, ci);
                    // @PostConstruct: CDI config-phase-end callback. Products skip it — the
                    // producer owns its product's initialization.
                    collectPostConstruct(bean, ci);
                }
                // @ConditionalOnBean is collected in Discovery (class beans in registerClass,
                // @Bean products in createFactoryBean — method-level AND product-class-level,
                // side by side). Not re-read here: BeanEnrichment only handles constructor params
                // and @Order.
                collectOrder(bean, ci);
            }
        }
        detectAopBindings(beans);
    }

    /**
     * Captures the {@code @Order} value at discovery time (Jandex), mirroring {@code
     * BeanContainer.orderOf}: the class annotation wins, else the first {@code @Order} found on an
     * implemented interface, else {@code MAX_VALUE}. The AOT engine uses {@code
     * BeanDefinition.order} to sort {@code List<T>} injection slices; the runtime sorts {@code
     * getBeans} by instance order — both must agree.
     */
    private void collectOrder(BeanDefinition bean, ClassInfo ci) {
        AnnotationInstance order = ci.classAnnotation(ORDER_DOT);
        if (order != null) {
            bean.order = order.value().asInt();
            return;
        }
        for (DotName iface : ci.interfaceNames()) {
            ClassInfo ifaceInfo = index.getClassByName(iface);
            if (ifaceInfo == null) continue;
            AnnotationInstance ifaceOrder = ifaceInfo.classAnnotation(ORDER_DOT);
            if (ifaceOrder != null) {
                bean.order = ifaceOrder.value().asInt();
                return;
            }
        }
    }

    // ── Lifecycle (@PostConstruct) ────────────────────────────────────

    /**
     * Captures the {@code @PostConstruct} method at discovery time (Jandex), following CDI
     * semantics: the most specific declaration in the class hierarchy wins (a subclass hides its
     * superclass's). Fail-fast on contract violations so both engines see a valid bean before any
     * instantiation.
     *
     * <p>Deliberately stricter than CDI on visibility: the method must be {@code public} — the AOT
     * engine emits a direct call into the generated context, so a package-private or private method
     * would silently break AOT compilation (CDI's reflection-based engines accept them).
     */
    private void collectPostConstruct(BeanDefinition bean, ClassInfo ci) {
        ClassInfo current = ci;
        while (current != null) {
            List<MethodInfo> lifecycle =
                    current.methods().stream()
                            .filter(m -> m.hasAnnotation(POST_CONSTRUCT_DOT))
                            .toList();
            if (!lifecycle.isEmpty()) {
                if (lifecycle.size() > 1) {
                    throw new BeanCreationException(
                            "Component "
                                    + bean.qualifiedName
                                    + " must declare at most one @PostConstruct method, found "
                                    + lifecycle.size());
                }
                MethodInfo method = lifecycle.get(0);
                validateLifecycleMethod(bean, method);
                bean.postConstructMethod = method.name();
                return;
            }
            DotName superName = current.superName();
            current = superName == null ? null : index.getClassByName(superName);
        }
    }

    private void validateLifecycleMethod(BeanDefinition bean, MethodInfo method) {
        String what = "Component " + bean.qualifiedName + " @PostConstruct method " + method.name();
        if ((method.flags() & 0x0001) == 0) { // not public
            throw new BeanCreationException(
                    what
                            + " must be public — the AOT engine emits a direct call (no"
                            + " reflection), so a non-public method would break AOT compilation");
        }
        if ((method.flags() & 0x0008) != 0) { // static
            throw new BeanCreationException(what + " must not be static");
        }
        if (method.parametersCount() > 0) {
            throw new BeanCreationException(what + " must not declare parameters");
        }
        if (!"void".equals(method.returnType().name().toString())) {
            throw new BeanCreationException(what + " must return void");
        }
    }

    // ── Constructor Params ────────────────────────────────────────────

    private void collectConstructorParams(BeanDefinition bean, ClassInfo ci) {
        List<MethodInfo> publicCtors =
                ci.methods().stream()
                        .filter(m -> m.name().equals("<init>") && (m.flags() & 0x0001) != 0)
                        .toList();
        if (publicCtors.isEmpty()) {
            throw new BeanCreationException(
                    "Component "
                            + bean.qualifiedName
                            + " must have exactly ONE public constructor. Found: 0");
        }
        if (publicCtors.size() > 1) {
            throw new BeanCreationException(
                    "Component "
                            + bean.qualifiedName
                            + " must have exactly ONE public constructor. Found: "
                            + publicCtors.size());
        }
        MethodInfo ctor = publicCtors.get(0);
        for (int i = 0; i < ctor.parametersCount(); i++) {
            bean.addParameter(JandexTypes.paramTypeName(ctor.parameterType(i), bean.qualifiedName));
        }
    }

    // ── AOP Bindings ──────────────────────────────────────────────────

    private void detectAopBindings(List<BeanDefinition> beans) {
        // Step 1: Collect all binding annotations (@InterceptorBinding-annotated)
        Set<DotName> bindingAnnotations = new HashSet<>();
        for (ClassInfo ci : index.getKnownClasses()) {
            if (ci.isAnnotation() && ci.hasAnnotation(INTERCEPTOR_BINDING_DOT)) {
                bindingAnnotations.add(ci.name());
            }
        }

        // Step 2: Identify interceptor beans and their binding annotations
        Map<BeanDefinition, Set<DotName>> interceptorBindings = new HashMap<>();
        for (BeanDefinition bean : beans) {
            if (bean instanceof ConfigPropertiesBean) continue;
            ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
            if (ci == null) continue;
            if (ci.annotation(INTERCEPTOR_DOT) == null) continue;
            Set<DotName> bindings = new HashSet<>();
            for (AnnotationInstance ann : ci.declaredAnnotations()) {
                if (bindingAnnotations.contains(ann.name())) {
                    bindings.add(ann.name());
                }
            }
            if (!bindings.isEmpty()) {
                interceptorBindings.put(bean, bindings);
            }
        }

        // Step 3: Populate interceptorBindingAnnotations and match interceptors
        for (BeanDefinition bean : beans) {
            if (bean instanceof ConfigPropertiesBean) continue;

            ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
            if (ci == null) continue;

            if (ci.annotation(INTERCEPTOR_DOT) != null) continue;

            Set<String> bindings = new HashSet<>();
            Set<DotName> targetBindings = new HashSet<>();
            for (AnnotationInstance ann : ci.declaredAnnotations()) {
                if (bindingAnnotations.contains(ann.name())) {
                    String name = ann.name().toString();
                    bindings.add(name);
                    targetBindings.add(ann.name());
                }
            }

            Map<String, Set<String>> methodBindings = new java.util.HashMap<>();
            if (targetBindings.isEmpty()) {
                for (MethodInfo method : ci.methods()) {
                    Set<String> methodAnnNames = new HashSet<>();
                    for (AnnotationInstance ann : method.annotations()) {
                        if (bindingAnnotations.contains(ann.name())) {
                            String name = ann.name().toString();
                            methodAnnNames.add(name);
                            bindings.add(name);
                        }
                    }
                    if (!methodAnnNames.isEmpty()) {
                        methodBindings.put(method.name(), methodAnnNames);
                    }
                }
            }

            // Binding annotations can also be declared on the bean's implemented
            // interfaces — both type-level and method-level. A proxied service usually
            // implements an interface (e.g. IssueService) that carries the binding
            // annotation on its methods; the implementing class inherits it but Jandex
            // ClassInfo.methods()/declaredAnnotations() do not include inherited
            // interface members. Without this walk the interceptor is never applied.
            for (String ifaceName : bean.interfaceNames) {
                ClassInfo ifaceCi = index.getClassByName(DotName.createSimple(ifaceName));
                if (ifaceCi == null) {
                    continue;
                }
                for (AnnotationInstance ann : ifaceCi.declaredAnnotations()) {
                    if (bindingAnnotations.contains(ann.name())) {
                        String name = ann.name().toString();
                        bindings.add(name);
                        targetBindings.add(ann.name());
                    }
                }
                for (MethodInfo method : ifaceCi.methods()) {
                    for (AnnotationInstance ann : method.annotations()) {
                        if (bindingAnnotations.contains(ann.name())) {
                            String name = ann.name().toString();
                            bindings.add(name);
                            methodBindings
                                    .computeIfAbsent(method.name(), k -> new HashSet<>())
                                    .add(name);
                        }
                    }
                }
            }

            bean.interceptorBindingAnnotations =
                    bindings.isEmpty() ? Set.of() : Set.copyOf(bindings);

            // A class-level binding (@Logged on the bean class) intercepts every
            // method. AotProxyGenerator keys that as "" (empty method name), so we
            // must record it there — BeanEnrichment otherwise only populates
            // methodBindingAnnotations with method-level entries (keyed by method
            // name). RUNTIME's ProxyFactory derives class-level coverage directly
            // from the implementation class annotations, so this key is the AOT
            // engine's signal that the whole bean is bound.
            Map<String, Set<String>> finalMethodBindings = new java.util.HashMap<>(methodBindings);
            if (!targetBindings.isEmpty()) {
                Set<String> classLevel =
                        targetBindings.stream()
                                .map(DotName::toString)
                                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
                finalMethodBindings.put("", classLevel);
            }
            if (!finalMethodBindings.isEmpty()) {
                bean.methodBindingAnnotations = finalMethodBindings;
            }

            if (!bindings.isEmpty()) {
                for (var entry : interceptorBindings.entrySet()) {
                    for (DotName binding : entry.getValue()) {
                        if (targetBindings.contains(binding)
                                || methodBindings.values().stream()
                                        .anyMatch(ms -> ms.contains(binding.toString()))) {
                            bean.interceptors.add(entry.getKey());
                            break;
                        }
                    }
                }
            }
        }
    }
}
