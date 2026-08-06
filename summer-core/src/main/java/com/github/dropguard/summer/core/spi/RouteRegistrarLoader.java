package com.github.dropguard.summer.core.spi;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.RouteInfo;
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
 * <p>This is the single place where SPI-level parameter bindings are converted into the
 * cross-engine {@link RouteInfo.ParamInfo} contract ({@code VALIDATED_BODY} collapses to {@code
 * BODY} + {@code validated}, so both engines treat {@code @Valid} uniformly).
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
                log.error("[Summer] RouteRegistrar failed: {}", registrar.getClass().getName(), e);
                throw new RuntimeException("Route registration failed", e);
            }
        }
        return result;
    }

    /**
     * Merges a {@link Result} into the candidate bean definitions: routes are appended to the
     * matching bean's {@link BeanDefinition#routes}, exception handlers to {@link
     * BeanDefinition#exceptionHandlerMethods}. Unknown bean names are logged and skipped.
     *
     * @param result the collected SPI contributions
     * @param candidates the candidate bean definitions to merge into
     */
    public static void mergeInto(Result result, List<BeanDefinition> candidates) {
        for (RouteInfo route : result.routes) {
            BeanDefinition target =
                    candidates.stream()
                            .filter(b -> b.qualifiedName.equals(route.controllerClass))
                            .findFirst()
                            .orElse(null);
            if (target != null) {
                target.routes.add(route);
            } else {
                log.warn("[Summer] Route registered for unknown bean: {}", route.controllerClass);
            }
        }
        for (Map.Entry<String, List<BeanDefinition.ExceptionHandlerEntry>> entry :
                result.exceptionHandlers.entrySet()) {
            BeanDefinition target =
                    candidates.stream()
                            .filter(b -> b.qualifiedName.equals(entry.getKey()))
                            .findFirst()
                            .orElse(null);
            if (target != null) {
                target.exceptionHandlerMethods.addAll(entry.getValue());
            } else {
                log.warn(
                        "[Summer] Exception handler registered for unknown bean: {}",
                        entry.getKey());
            }
        }
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
                List<ParamInfo> parameters) {
            List<RouteInfo.ParamInfo> routeParams = new ArrayList<>();
            for (ParamInfo spiParam : parameters) {
                boolean validated =
                        spiParam.validated || spiParam.binding == ParamBinding.VALIDATED_BODY;
                RouteInfo.ParamBinding binding =
                        spiParam.binding == ParamBinding.VALIDATED_BODY
                                ? RouteInfo.ParamBinding.BODY
                                : convertBinding(spiParam.binding);
                routeParams.add(
                        new RouteInfo.ParamInfo(
                                spiParam.name,
                                spiParam.bindingName,
                                spiParam.type.getName(),
                                binding,
                                validated));
            }
            RouteInfo route =
                    new RouteInfo(
                            httpMethod,
                            path,
                            bean.qualifiedName,
                            handlerMethodName,
                            "void" // return type not used by SPI
                            );
            route.params.addAll(routeParams);
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

        private RouteInfo.ParamBinding convertBinding(ParamBinding binding) {
            return switch (binding) {
                case PATH -> RouteInfo.ParamBinding.PATH;
                case QUERY -> RouteInfo.ParamBinding.QUERY;
                case BODY -> RouteInfo.ParamBinding.BODY;
                case CONTEXT -> RouteInfo.ParamBinding.CONTEXT;
                case REQUEST -> RouteInfo.ParamBinding.REQUEST;
                case RESPONSE -> RouteInfo.ParamBinding.RESPONSE;
                case HEADER -> RouteInfo.ParamBinding.HEADER;
                case COOKIE -> RouteInfo.ParamBinding.COOKIE;
                case PAGEABLE -> RouteInfo.ParamBinding.PAGEABLE;
                case SCROLL -> RouteInfo.ParamBinding.SCROLL;
                case PRINCIPAL -> RouteInfo.ParamBinding.PRINCIPAL;
                // VALIDATED_BODY is handled by the caller (mapped to BODY + validated=true)
                default -> RouteInfo.ParamBinding.UNKNOWN;
            };
        }
    }
}
