package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.RuntimeDiMarker;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.core.config.ConfigBinder;
import com.github.dropguard.summer.core.validation.Result;
import com.github.dropguard.summer.core.validation.Validator;
import com.github.dropguard.summer.engine.BeanDeployment;
import com.github.dropguard.summer.engine.BuildPipeline;
import com.github.dropguard.summer.engine.ContainerEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
public final class RuntimeContainer implements ContainerEngine {

    private static final Logger log = LoggerFactory.getLogger(RuntimeContainer.class);

    @Override
    public Engine engine() {
        return Engine.RUNTIME;
    }

    // ── SPI entry (ContainerEngines.forEngine()) ──────────────────────

    @Override
    public BeanContainer build(
            BeanDeployment deployment, MockedBean[] mocks, Map<String, Object> overrides) {
        return init(deployment, List.of(mocks), overrides);
    }

    // ── shared pipeline ───────────────────────────────────────────────

    static BeanContainer init(
            BeanDeployment deployment,
            List<MockedBean> mocks,
            Map<String, Object> overrides,
            Object... externalBeans) {
        BeanContainer.Builder builder = new BeanContainer.Builder();

        deployment.addSyntheticBean(
                RuntimeDiMarker.class,
                new RuntimeDiMarker(),
                "new com.github.dropguard.summer.core.RuntimeDiMarker()");

        log.info(
                "[Summer] BeanDeployment: archives={} syntheticBeans={}",
                deployment.archives(),
                deployment.syntheticBeans().stream()
                        .map(b -> b.qualifiedName)
                        .collect(java.util.stream.Collectors.joining(",")));

        // Shared core, stage 1: discovery → conditions (+mock removal) → route collection — the
        // same single sequence as the AOT entries, so dual-engine parity is structural.
        List<BeanDefinition> candidates = BuildPipeline.discoverCandidates(deployment, mocks);

        // Runtime-only contribution derived from the surviving candidates: collect exception
        // handlers (SPI-contributed + already present) into the synthetic HandlerMetadata bean
        // consumed by the web exception registry. Injected before resolution so beans that
        // reference it resolve.
        Map<String, List<BeanDefinition.ExceptionHandlerEntry>> handlerMap = new HashMap<>();
        for (BeanDefinition bd : candidates) {
            if (!bd.exceptionHandlerMethods.isEmpty()) {
                handlerMap.merge(
                        bd.qualifiedName,
                        bd.exceptionHandlerMethods,
                        (a, b) -> {
                            List<BeanDefinition.ExceptionHandlerEntry> merged = new ArrayList<>(a);
                            merged.addAll(b);
                            return merged;
                        });
            }
        }
        BeanDefinition handlerMetaDef =
                new BeanDefinition(HandlerMetadata.class.getName(), "HandlerMetadata");
        handlerMetaDef.syntheticInstance = new HandlerMetadata(handlerMap);
        candidates.add(handlerMetaDef);

        // Shared core, stage 2: dependency resolution → variable-name dedup → route extraction.
        BuildPipeline.Resolved resolved = BuildPipeline.resolve(candidates, mocks);
        List<BeanDefinition> sorted = resolved.sorted();

        RuntimeConfigBinder binder = new RuntimeConfigBinder();
        ConfigBinder.BindingContext ctx = ConfigBinder.BindingContext.of(overrides);
        // Config binding only needs the surviving set and must land before instantiation — it is
        // independent of route/condition order, so it runs after the shared core.
        bindConfiguration(sorted, builder, binder, ctx);

        Map<String, List<String>> interceptorMap = buildInterceptorMap(sorted);
        Map<String, Set<String>> bindingMap = new HashMap<>();
        for (BeanDefinition bd : sorted) {
            if (!bd.interceptorBindingAnnotations.isEmpty()) {
                bindingMap.put(bd.qualifiedName, bd.interceptorBindingAnnotations);
            }
        }
        BeanInstantiator instantiator =
                new BeanInstantiator(
                        builder,
                        interceptorMap,
                        bindingMap,
                        resolved.interfaceImplementationCounts());

        for (MockedBean mocked : mocks) {
            builder.register(mocked.targetType(), mocked.instance());
        }
        // External beans must be registered BEFORE the sorted definitions are instantiated: a bean
        // whose constructor needs an external bean resolves it via builder.getBean(type) at
        // instantiation time, so a late registration would throw NoSuchBeanException. Both mocks
        // and external beans register only their declared type — no interface keys needed:
        // BeanContainer.getBean resolves by assignability (type.isInstance scans the registry).
        for (Object bean : externalBeans) {
            if (bean != null) {
                builder.register(bean.getClass(), bean);
            }
        }
        for (BeanDefinition bd : sorted) {
            log.debug(
                    "[Summer] Instantiating bean {} [factory {}#{}] archive={} params={}{}",
                    bd.qualifiedName,
                    bd.configClassName,
                    bd.producerMethodName,
                    bd.archiveName,
                    bd.parameters.size(),
                    bd.syntheticInstance != null ? " [synthetic]" : "");
            instantiator.instantiateFromDefinition(bd);
        }
        builder.routes(resolved.routes());

        // validators — single pass, single Result, single throw
        Result validationResult = new Result();
        for (Object bean : builder.singletons().values()) {
            if (bean instanceof Validator<?> v) {
                List<?> targets = builder.getBeans(v.targetType());
                for (Object target : targets) {
                    @SuppressWarnings("unchecked")
                    Validator<Object> typed = (Validator<Object>) v;
                    typed.validate(target, validationResult);
                }
            }
        }
        validationResult.throwIfInvalid();

        log.info(
                "[Summer] Built RUNTIME container: {} beans, {} routes",
                sorted.size(),
                resolved.routes().size());
        return builder.build(Engine.RUNTIME);
    }

    private static void bindConfiguration(
            List<BeanDefinition> candidates,
            BeanContainer.Builder builder,
            RuntimeConfigBinder binder,
            ConfigBinder.BindingContext ctx) {
        for (BeanDefinition bd : candidates) {
            if (!(bd instanceof ConfigPropertiesBean c)) continue;
            Class<?> configClass;
            try {
                configClass = Class.forName(c.qualifiedName);
            } catch (ClassNotFoundException e) {
                log.debug("[Summer] Could not load @ConfigMapping class: {}", c.qualifiedName);
                continue;
            }
            if (builder.peek(configClass) != null) continue;
            String prefix = c.configPropertiesPrefix;
            // @WithDefault is read directly from method annotations by bindInterface,
            // so only profile overrides are passed via BindingContext.
            builder.register(configClass, binder.bind(ctx, prefix, configClass));
            log.debug(
                    "[Summer] Bound config properties: {} (prefix='{}')",
                    configClass.getSimpleName(),
                    prefix);
        }
    }

    /**
     * Builds the interceptor map for proxy generation from the discovery-time {@link
     * BeanDefinition#interceptors} edges. {@code needsProxy()} already excludes interceptor beans
     * themselves, so no further filtering is needed.
     */
    private static Map<String, List<String>> buildInterceptorMap(List<BeanDefinition> allBeans) {
        return allBeans.stream()
                .filter(BeanDefinition::needsProxy)
                .map(
                        bean ->
                                Map.entry(
                                        bean.qualifiedName,
                                        bean.interceptors.stream()
                                                .map(b -> b.qualifiedName)
                                                .toList()))
                .filter(e -> !e.getValue().isEmpty())
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
