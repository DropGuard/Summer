package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Discovery;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.RuntimeDiMarker;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.BeanDeployment;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.core.bean.RouteInfo;
import com.github.dropguard.summer.core.bean.SharedConditionEvaluator;
import com.github.dropguard.summer.core.bean.SharedDependencyResolver;
import com.github.dropguard.summer.core.config.ConfigBinder;
import com.github.dropguard.summer.core.config.ConfigurationProperties;
import com.github.dropguard.summer.core.config.TypeConverter;
import com.github.dropguard.summer.core.validation.Validator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds {@link BeanContainer} for the Runtime DI engine.
 *
 * <pre>{@code
 * BeanContainer ctx = DiEngine.create(Engine.RUNTIME);
 * }</pre>
 *
 * <p>For isolated builds (tests), use {@code Testing.build()} over the full test universe, or
 * {@code Testing.build()} for the TCK path.
 */
public final class RuntimeBeanContainerBuilder {

    private static final Logger log = LoggerFactory.getLogger(RuntimeBeanContainerBuilder.class);

    private RuntimeBeanContainerBuilder() {}

    /**
     * Builds a {@link BeanContainer} over the test universe: the whole application (every
     * production bean) plus the test-class beans on the classpath. Mirrors Quarkus'
     * {@code @QuarkusTest} universe — no seed list, no module narrowing. Test beans are discovered
     * exactly like production beans.
     *
     * @param mocks mocked beans produced from {@code @Mock} parameters (internal)
     * @return immutable bean container
     */
    @Internal
    public static BeanContainer build(List<MockedBean> mocks) {
        return build(testUniverse(), mocks, java.util.Map.of());
    }

    /**
     * Convenience overload for the no-mock, no-override test universe (equivalent to {@link
     * #build(List)} with an empty mock list). Retained so existing tests that call {@code build()}
     * without mocks keep compiling.
     *
     * @return immutable bean container over the full test universe
     */
    public static BeanContainer build() {
        return build(List.of());
    }

    /**
     * Production bootstrap entry point, invoked reflectively by {@code DiEngine.create} for the
     * Runtime engine (dev mode / IDE). Discovers the full application components and registers the
     * boot-time external beans supplied by {@code SummerApplication} (e.g. the ordered middleware
     * list from {@code apply(...)}). Test containers never use this overload — they go through
     * {@link #build(List)} and must not hand-register beans.
     */
    public static BeanContainer build(Object... externalBeans) {
        JandexIndexLoader.LoadedIndex prod = JandexIndexLoader.productionIndex();
        BeanDeployment deployment =
                BeanDeployment.forProduction(
                        prod.index(), prod.classToArchive(), prod.archiveIndexes());
        return initialize(deployment, List.of(), java.util.Map.of(), externalBeans);
    }

    /**
     * Test container build with explicit {@code @TestProfile} overrides over the full test
     * universe. The universe is always the whole application plus test beans on the classpath —
     * discovery never loads a class outside it, and both engines observe the same candidate set.
     *
     * @param mocks mocked beans produced from {@code @Mock} parameters (internal)
     * @param overrides resolved {@code @TestProfile} content (empty map when none)
     * @return immutable bean container
     */
    @Internal
    public static BeanContainer build(
            List<MockedBean> mocks, java.util.Map<String, Object> overrides) {
        return build(testUniverse(), mocks, overrides);
    }

    /**
     * Builds a {@link BeanContainer} over a caller-supplied narrow {@link IndexView} (e.g. a seed +
     * transitive-closure index from {@code @SummerTest(classes=...)}). The classpath-type mapping
     * is collapsed to a single synthetic module so {@code discoverComponents} enumerates exactly
     * the classes in the supplied index — no production or unrelated test beans leak in. Discovery,
     * mock, and profile handling are identical to the full-universe path; only the candidate set
     * shrinks.
     */
    @Internal
    public static BeanContainer build(
            IndexView narrowIndex,
            List<MockedBean> mocks,
            java.util.Map<String, Object> overrides) {
        BeanDeployment deployment = BeanDeployment.forNarrow(narrowIndex);
        return initialize(deployment, mocks, overrides);
    }

    /**
     * Builds a {@link BeanContainer} over a caller-supplied {@link BeanDeployment}. The test
     * container uses the whole-universe deployment (application archives plus test archives); the
     * production path is not exposed here (production startup uses {@code SummerApplication} /
     * {@code SummerMojo}, which read only {@code jandex.idx}).
     *
     * @param deployment the deployment to discover from (test universe)
     * @param mocks mocked beans produced from {@code @Mock} parameters (internal)
     * @param overrides resolved {@code @TestProfile} content (empty map when none)
     * @return immutable bean container
     */
    @Internal
    public static BeanContainer build(
            BeanDeployment deployment,
            List<MockedBean> mocks,
            java.util.Map<String, Object> overrides) {
        return initialize(deployment, mocks, overrides);
    }

    /**
     * The whole-universe test deployment, faithful to Quarkus' {@code @QuarkusTest}: the production
     * application archives plus exactly the current test class's {@code test-classes} directory,
     * indexed on demand via {@link TestClassIndexer} (never a pre-baked {@code jandex-test.idx},
     * never a bulk scan of every module's test classes, never an exclude list). A negative
     * (sad-path) fixture lives in its own module and is simply not on the path of any application's
     * {@code test-classes} directory, so it cannot enter the whole-universe container —
     * structurally, with no allow-list.
     *
     * <p>When no explicit test class is available (e.g. {@code build()} called without one), the
     * calling test class is inferred from the stack so the same single-directory model still
     * applies.
     */
    private static BeanDeployment testUniverse() {
        JandexIndexLoader.LoadedIndex prod = JandexIndexLoader.productionIndex();
        Class<?> inferred = TestClassIndexer.inferTestClass();
        IndexView testIndex =
                (inferred != null)
                        ? TestClassIndexer.indexTestClasses(inferred)
                        : CompositeIndex.create(List.of());
        // Per-archive attribution: every production module, plus the test-classes
        // directory as one "test" archive (Quarkus adds the test-classes archive as
        // a single additional archive).
        Map<String, String> classToArchive = new java.util.HashMap<>(prod.classToArchive());
        for (String type : testIndex.getKnownClasses().stream().map(Object::toString).toList()) {
            classToArchive.put(type, "test");
        }
        Map<String, IndexView> archiveIndexes = new java.util.HashMap<>(prod.archiveIndexes());
        archiveIndexes.put("test", testIndex);
        return BeanDeployment.forTestUniverse(prod.index(), classToArchive, archiveIndexes);
    }

    private static BeanContainer initialize(
            BeanDeployment deployment,
            List<MockedBean> mocks,
            java.util.Map<String, Object> overrides,
            Object... externalBeans) {
        BeanContainer.Builder builder = new BeanContainer.Builder();

        // Engine-provided (synthetic) beans. The discovery IndexView is declared on
        // the blueprint (BeanDeployment) so both engines merge it into the candidate
        // set identically; RuntimeDiMarker is runtime-only and declared here. Discovery
        // folds both into the returned beansView, so neither engine hand-registers
        // them.
        deployment.addSyntheticBean(
                RuntimeDiMarker.class,
                new RuntimeDiMarker(),
                "new com.github.dropguard.summer.core.RuntimeDiMarker()");

        // ── Phase 1: Discovery ──────────────────────────────────────
        // Unified discovery shared with the AOT engine: both engines observe the
        // same candidate set (scanned beans + synthetic beans), so dual-engine parity
        // is structural, not conventional. Discovery yields enriched BeanDefinitions
        // directly (no Class.forName at discovery time — class loading is deferred to
        // the instantiator, matching AOT). The AotDiMarker is registered by Discovery
        // itself; IndexView and RuntimeDiMarker arrive as synthetic beans above.
        log.info(
                "[Summer] BeanDeployment: archives={} syntheticBeans={}",
                deployment.archives(),
                deployment.syntheticBeans().stream()
                        .map(b -> b.qualifiedName)
                        .collect(java.util.stream.Collectors.joining(",")));
        List<BeanDefinition> candidates = Discovery.discover(deployment);

        // ── Phase 2: Evaluation ─────────────────────────────────────
        // Evaluate @ConditionalOnBean and @Replaces against the scoped candidate
        // set, and remove real beans whose type is mocked. Condition targets and
        // mock replacements are evaluated identically on both engines (the AOT path
        // feeds the same MockedBean target types into SharedConditionEvaluator).
        Set<String> mockedTypeNames =
                mocks.stream()
                        .map(MockedBean::targetTypeName)
                        .collect(java.util.stream.Collectors.toSet());
        new SharedConditionEvaluator().evaluate(candidates, mockedTypeNames, deployment);

        // ── Phase 3: Resolution ─────────────────────────────────────
        // Bind, sort, instantiate. A single BindingContext carries the parsed
        // YAML (cached) and any @TestProfile overrides — no ThreadLocal, no
        // remove() discipline. The overrides parameter is consumed only here, at
        // the binding step; it does not thread through the rest of initialization.
        ConfigBinder.BindingContext ctx = ConfigBinder.BindingContext.of(overrides);
        bindConfigurationProperties(candidates, builder, ctx);
        BeanDefinitionFactory.populateInterceptors(candidates);

        // Pre-build exception handler metadata for RuntimeExceptionHandlerRegistrar.
        // This eliminates reflection-based @ExceptionHandler scanning at registration
        // time.
        RuntimeExceptionHandlerRegistrar.setPrebuiltHandlers(candidates);

        SharedDependencyResolver resolver = new SharedDependencyResolver();
        List<BeanDefinition> sorted = resolver.resolve(candidates, mocks);

        Map<String, List<String>> interceptorMap =
                BeanDefinitionFactory.buildInterceptorMap(candidates);
        Map<String, Set<String>> interceptorBindingMap = new HashMap<>();
        for (BeanDefinition bd : candidates) {
            if (!bd.interceptorBindingAnnotations.isEmpty()) {
                interceptorBindingMap.put(bd.qualifiedName, bd.interceptorBindingAnnotations);
            }
        }
        BeanInstantiator instantiator =
                new BeanInstantiator(builder, interceptorMap, interceptorBindingMap);

        // Register mocked instances under their declared target type (and the
        // target's interfaces) so dependent beans inject the mock. The real bean of
        // each target type was already removed at discovery stage, so this never
        // collides with a real instance.
        for (MockedBean mocked : mocks) {
            builder.register(mocked.targetType(), mocked.instance());
            for (Class<?> iface : mocked.targetType().getInterfaces()) {
                builder.register(iface, mocked.instance());
            }
        }
        for (BeanDefinition beanDef : sorted) {
            log.debug(
                    "[Summer] Instantiating bean {} [factory {}#{}] archive={} params={}{}",
                    beanDef.qualifiedName,
                    beanDef.configClassName,
                    beanDef.producerMethodName,
                    beanDef.archiveName,
                    beanDef.parameters.size(),
                    beanDef.syntheticInstance != null ? " [synthetic]" : "");
            instantiator.instantiateFromDefinition(beanDef);
        }
        // Collect route metadata from candidates for route registration
        List<RouteInfo> allRoutes = candidates.stream().flatMap(bd -> bd.routes.stream()).toList();
        builder.routes(allRoutes);

        runValidators(builder);

        // Boot-time external beans (production only): register under concrete class.
        for (Object bean : externalBeans) {
            if (bean != null) {
                builder.register(bean.getClass(), bean);
            }
        }

        log.info(
                "[Summer] Built RUNTIME container: {} beans, {} routes",
                sorted.size(),
                allRoutes.size());
        return builder.build(Engine.RUNTIME);
    }

    // ---- @ConfigurationProperties binding ----

    /**
     * Binds every {@code @ConfigurationProperties} bean discovered in the candidate set. Reads the
     * Jandex-extracted {@link ConfigPropertiesBean} (prefix + {@code @DefaultValue} metadata) so
     * binding is identical to the AOT engine, which consumes the same {@link BeanDefinition}
     * fields. Class loading is deferred to here (one {@code Class.forName} per config bean),
     * keeping discovery reflection-free.
     */
    private static void bindConfigurationProperties(
            List<BeanDefinition> candidates,
            BeanContainer.Builder builder,
            ConfigBinder.BindingContext ctx) {
        for (BeanDefinition beanDef : candidates) {
            if (!(beanDef instanceof ConfigPropertiesBean configBean)) {
                continue;
            }
            Class<?> configClass;
            try {
                configClass = Class.forName(configBean.qualifiedName);
            } catch (ClassNotFoundException e) {
                log.debug(
                        "[Summer] Could not load @ConfigurationProperties class: {}",
                        configBean.qualifiedName);
                continue;
            }
            if (builder.peek(configClass) != null) {
                continue;
            }
            ConfigurationProperties props =
                    configClass.getAnnotation(ConfigurationProperties.class);
            if (props == null) {
                continue;
            }
            // Convert the Jandex-extracted @DefaultValue strings into their declared
            // types and feed them through the BindingContext, mirroring AOT's baked-in
            // defaults — no separate reflection pass needed.
            Map<String, Object> defaults = new HashMap<>();
            for (var entry : configBean.defaultValues.entrySet()) {
                String fieldType = configBean.fieldTypes.get(entry.getKey());
                if (fieldType != null) {
                    try {
                        defaults.put(
                                entry.getKey(),
                                TypeConverter.convert(entry.getValue(), Class.forName(fieldType)));
                    } catch (Exception ignored) {
                        // leave unconverted; ConfigBinder handles the raw string
                    }
                }
            }
            ConfigBinder.BindingContext perClassCtx =
                    defaults.isEmpty()
                            ? ctx
                            : ConfigBinder.BindingContext.of(defaults, ctx.overrides());
            Object instance = ConfigBinder.bind(perClassCtx, props.prefix(), configClass);
            builder.register(configClass, instance);
            log.debug(
                    "[Summer] Bound @ConfigurationProperties: {} (prefix='{}')",
                    configClass.getSimpleName(),
                    props.prefix());
        }
    }

    // ---- Validation ----

    @SuppressWarnings("unchecked")
    private static void runValidators(BeanContainer.Builder builder) {
        for (Object bean : builder.singletons().values()) {
            if (bean instanceof Validator<?> validator) {
                Class<?> targetType = validator.targetType();
                Object target = builder.peek(targetType);
                if (target != null) {
                    ((Validator<Object>) validator).validate(target);
                }
            }
        }
    }
}
