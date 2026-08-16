package com.github.dropguard.summer.engine;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.core.bean.RouteInfo;
import com.github.dropguard.summer.core.bean.SharedDependencyResolver;
import com.github.dropguard.summer.core.spi.RouteRegistrarLoader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The shared assembly core for the fixed build-time sequence: discovery → condition evaluation →
 * route collection → dependency resolution → variable-name dedup → route extraction.
 *
 * <p>One order, one implementation: the build-time code generator (summer-maven-plugin), the
 * test-time AOT compiler ({@code AotEngine}), and the runtime engine ({@code RuntimeContainer}) all
 * run the same core, so dual-entry / dual-engine parity stops being a comment convention and
 * becomes a single code path. The core is exposed as two stages so the runtime engine can
 * interleave its own mid-sequence contribution (the synthetic {@code HandlerMetadata}, derived from
 * the surviving candidates and injected before resolution); the AOT entries use the one-shot {@link
 * #resolve(BeanDeployment, List)}.
 *
 * <p>Condition evaluation runs BEFORE route collection by contract: a conditioned-out controller
 * never reaches the route registrars, matching the engines' documented parity rule (the build-time
 * Mojo previously merged routes first and diverged). The fixed sequence is deliberate — Summer's
 * assembly stages are domain-ordered, so a Quarkus-style free build-step graph would add ordering
 * cost without expressiveness.
 */
@Internal
public final class BuildPipeline {

    private static final Logger log = LoggerFactory.getLogger(BuildPipeline.class);

    private BuildPipeline() {}

    /**
     * The resolved bean graph plus the route metadata collected during assembly, and the interface
     * implementation counts (interface name -> number of beans implementing it). Registration
     * phases (Runtime BeanInstantiator, AOT WireMethodGenerator) use the counts to register an
     * interface key only for single-impl interfaces (supports getBean(iface) / constructor
     * injection by interface); multi-impl interfaces are collection-injection strategies resolved
     * via getBeans, never by a shared key.
     */
    public record Resolved(
            List<BeanDefinition> sorted,
            List<RouteInfo> routes,
            java.util.Map<String, Integer> interfaceImplementationCounts) {}

    /**
     * Runs the whole shared core for a deployment — the one-shot form the AOT entries use.
     *
     * @param deployment the deployment to assemble (its archives drive discovery)
     * @param mocks test mocks; empty for the production build
     * @return the resolved, topologically-sorted beans and the collected routes
     */
    public static Resolved resolve(BeanDeployment deployment, List<MockedBean> mocks) {
        return resolve(discoverCandidates(deployment, mocks), mocks);
    }

    /**
     * Stage 1 of the shared core: discovery → condition evaluation (mock removal included) → route
     * collection. Returns the surviving candidates, pre-resolution — the hook point for an engine
     * tail that must derive its own contribution from the candidates and inject it before {@link
     * #resolve(List, List)} (the runtime engine's synthetic {@code HandlerMetadata}).
     */
    public static List<BeanDefinition> discoverCandidates(
            BeanDeployment deployment, List<MockedBean> mocks) {
        List<BeanDefinition> beans = Discovery.discover(deployment);
        log.info("[Summer] Discovered {} beans", beans.size());

        Set<String> mockedTypeNames =
                mocks.stream().map(MockedBean::targetTypeName).collect(Collectors.toSet());
        new SharedConditionEvaluator().evaluate(beans, mockedTypeNames);

        // Conditions BEFORE routes: a conditioned-out controller contributes no routes (parity).
        RouteRegistrarLoader.mergeInto(RouteRegistrarLoader.load(beans), beans);
        return beans;
    }

    /**
     * Stage 2 of the shared core: dependency resolution → variable-name dedup → route extraction.
     *
     * @param candidates the surviving candidates (from {@link #discoverCandidates}, plus any
     *     engine-tail contributions)
     * @param mocks test mocks; empty for the production build
     * @return the resolved, topologically-sorted beans and the collected routes
     */
    public static Resolved resolve(List<BeanDefinition> candidates, List<MockedBean> mocks) {
        List<BeanDefinition> sorted = new SharedDependencyResolver().resolve(candidates, mocks);
        log.info("[Summer] Resolved {} beans", sorted.size());
        if (log.isDebugEnabled()) {
            for (BeanDefinition bean : sorted) {
                log.debug(
                        "[Summer]   bean: {} [factory {}#{}] archive={} params={}{}",
                        bean.qualifiedName,
                        bean.configClassName,
                        bean.producerMethodName,
                        bean.archiveName,
                        bean.parameters.size(),
                        bean.syntheticInstance != null ? " [synthetic]" : "");
            }
        }

        dedupVariableNames(sorted);

        List<RouteInfo> routes = sorted.stream().flatMap(bd -> bd.routes.stream()).toList();
        java.util.Map<String, Integer> ifaceCounts =
                SharedDependencyResolver.interfaceImplementationCounts(sorted);
        return new Resolved(sorted, routes, ifaceCounts);
    }

    /**
     * Makes each bean's codegen variable name unique (the generated wire method declares one
     * variable per bean; names derive from the simple class name, so same-simple-name beans across
     * packages collide). The runtime engine ignores {@code variableName}. Idempotent: a graph with
     * no collisions is untouched.
     */
    private static void dedupVariableNames(List<BeanDefinition> sorted) {
        Set<String> usedNames = new HashSet<>();
        for (BeanDefinition bean : sorted) {
            String baseName = bean.variableName;
            int suffix = 2;
            while (!usedNames.add(bean.variableName)) {
                bean.variableName = baseName + suffix++;
            }
        }
    }
}
