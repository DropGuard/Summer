package com.github.dropguard.summer.core.spi;

import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.RouteInfo;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.core.exception.SummerException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads all {@link RouteRegistrar} implementations via {@link ServiceLoader} and collects their
 * route / exception-handler contributions into cross-engine metadata.
 *
 * <p>Shared by both DI engines: the Runtime engine merges the result into its {@link
 * BeanDefinition} candidates at container build time; the AOT engine merges it during {@code
 * wire()} generation. If no {@code RouteRegistrar} is found on the classpath (i.e. {@code
 * summer-web} is absent), the result is empty and the container runs in pure-DI mode.
 *
 * <p>This is the single collection point for route metadata: registrars contribute {@link
 * RouteInfo} parameter bindings directly, in the same cross-engine shape both engines consume.
 */
@Internal
public final class RouteRegistrarLoader {

    /** Cross-engine result of running every available {@code RouteRegistrar}. */
    @Internal
    public static final class Result {
        public final List<RouteInfo> routes = new ArrayList<>();
        public final Map<String, List<BeanDefinition.ExceptionHandlerEntry>> exceptionHandlers =
                new HashMap<>();
    }

    private static final Logger log = LoggerFactory.getLogger(RouteRegistrarLoader.class);

    private RouteRegistrarLoader() {}

    /**
     * Loads every {@code RouteRegistrar} on the classpath and collects its contributions.
     *
     * @param beans the candidate bean definitions to scan
     * @return the merged routes and exception handlers (empty when no registrar is present)
     */
    public static Result load(List<BeanDefinition> beans) {
        Result result = new Result();
        List<RouteRegistrar> registrars = new ArrayList<>();
        ServiceLoader.load(RouteRegistrar.class).forEach(registrars::add);

        if (registrars.isEmpty()) {
            log.debug(
                    "[Summer] No RouteRegistrar found on classpath — running in pure-DI mode (no"
                            + " web routes).");
            return result;
        }

        log.debug(
                "[Summer] Found {} RouteRegistrar implementation(s), registering routes...",
                registrars.size());
        RouteRegistry registry = new CollectingRegistry(result);
        for (RouteRegistrar registrar : registrars) {
            try {
                registrar.register(registry, beans);
            } catch (Exception e) {
                // No double report: the exception below carries the registrar's identity and the
                // ErrorCode, and propagates to the one place that logs a startup failure.
                throw new SummerException(
                        ErrorCode.ROUTE_CONFLICT,
                        "Route registration failed in " + registrar.getClass().getName(),
                        e);
            }
        }
        return result;
    }

    /**
     * Merges a {@link Result} into the candidate bean definitions: routes are appended to the
     * matching bean's {@link BeanDefinition#routes}, exception handlers to {@link
     * BeanDefinition#exceptionHandlerMethods}.
     *
     * <p>Routes/handlers registered for a bean that is not among the candidates fail fast. The
     * registrar that produced them scanned the same {@code candidates} list (the runtime and AOT
     * engines both pass the same list to {@link #load} and then here), and condition-evaluation
     * already ran before collection — so a missing candidate is either a stale Jandex index or an
     * inconsistency between registrars, never a legitimately conditioned-out controller. Silently
     * dropping it would surface as an opaque 404 in production.
     *
     * @param result the collected SPI contributions
     * @param candidates the candidate bean definitions to merge into
     */
    public static void mergeInto(Result result, List<BeanDefinition> candidates) {
        for (RouteInfo route : result.routes) {
            BeanDefinition target = findCandidate(candidates, route.controllerClass);
            if (target != null) {
                target.routes.add(route);
            } else {
                throw new BeanCreationException(
                        "Route registered for unknown bean: "
                                + route.controllerClass
                                + " ("
                                + route.httpMethod
                                + " "
                                + route.path
                                + ") — the bean is not among the container's candidates. Stale"
                                + " Jandex index or a RouteRegistrar inconsistency?");
            }
        }
        for (Map.Entry<String, List<BeanDefinition.ExceptionHandlerEntry>> entry :
                result.exceptionHandlers.entrySet()) {
            BeanDefinition target = findCandidate(candidates, entry.getKey());
            if (target != null) {
                target.exceptionHandlerMethods.addAll(entry.getValue());
            } else {
                throw new BeanCreationException(
                        "Exception handler registered for unknown bean: "
                                + entry.getKey()
                                + " — the bean is not among the container's candidates. Stale"
                                + " Jandex index or a RouteRegistrar inconsistency?");
            }
        }
    }

    private static BeanDefinition findCandidate(List<BeanDefinition> candidates, String name) {
        for (BeanDefinition bean : candidates) {
            if (bean.qualifiedName.equals(name)) {
                return bean;
            }
        }
        return null;
    }

    private static final class CollectingRegistry implements RouteRegistry {
        private final Result result;

        CollectingRegistry(Result result) {
            this.result = result;
        }

        @Override
        public void registerRoute(
                BeanDefinition bean,
                String httpMethod,
                String path,
                String handlerMethodName,
                List<RouteInfo.ParamInfo> parameters) {
            // Registrars speak the cross-engine RouteInfo.ParamInfo contract directly — no
            // SPI-to-bean conversion layer (the old parallel ParamInfo/ParamBinding pair plus
            // its 12-branch identity switch were refactor leftovers and are gone).
            RouteInfo route =
                    new RouteInfo(httpMethod, path, bean.qualifiedName, handlerMethodName);
            route.params.addAll(parameters);
            result.routes.add(route);
        }

        @Override
        public void registerExceptionHandler(
                BeanDefinition bean,
                String handlerMethodName,
                String exceptionType,
                int parameterCount) {
            result.exceptionHandlers
                    .computeIfAbsent(bean.qualifiedName, k -> new ArrayList<>())
                    .add(
                            new BeanDefinition.ExceptionHandlerEntry(
                                    handlerMethodName, exceptionType, parameterCount));
        }
    }
}
