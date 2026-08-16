package com.github.dropguard.summer.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.RouteInfo;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Merge-phase contract for {@link RouteRegistrarLoader#mergeInto}. */
class RouteRegistrarLoaderTest {

    private static BeanDefinition bean(String qualifiedName) {
        BeanDefinition bean = new BeanDefinition(qualifiedName, "Simple");
        return bean;
    }

    private static RouteInfo route(String controllerClass) {
        RouteInfo route = new RouteInfo("GET", "/users", controllerClass, "listUsers");
        route.params.add(
                new RouteInfo.ParamInfo(
                        "id", "", "java.lang.String", RouteInfo.ParamBinding.PATH, false));
        return route;
    }

    @Test
    void mergesRouteIntoKnownBean() {
        BeanDefinition candidate = bean("com.example.UserController");
        RouteRegistrarLoader.Result result = new RouteRegistrarLoader.Result();
        result.routes.add(route("com.example.UserController"));

        RouteRegistrarLoader.mergeInto(result, List.of(candidate));

        assertEquals(1, candidate.routes.size(), "route must be appended to the matching bean");
        assertEquals("/users", candidate.routes.get(0).path);
    }

    @Test
    void mergesExceptionHandlerIntoKnownBean() {
        BeanDefinition candidate = bean("com.example.ErrorHandler");
        RouteRegistrarLoader.Result result = new RouteRegistrarLoader.Result();
        result.exceptionHandlers
                .computeIfAbsent("com.example.ErrorHandler", k -> new java.util.ArrayList<>())
                .add(
                        new BeanDefinition.ExceptionHandlerEntry(
                                "onIllegalArgument", "java.lang.IllegalArgumentException", 2));

        RouteRegistrarLoader.mergeInto(result, List.of(candidate));

        assertEquals(
                1,
                candidate.exceptionHandlerMethods.size(),
                "exception handler must be appended to the matching bean");
    }

    @Test
    void routeForUnknownBeanFailsFast() {
        // The registrar scanned the same candidate list the merge receives, so a route whose
        // controller is missing from candidates is a real error (stale index / registrar
        // inconsistency) — it must not silently 404 in production.
        RouteRegistrarLoader.Result result = new RouteRegistrarLoader.Result();
        result.routes.add(route("com.example.GhostController"));

        BeanCreationException ex =
                assertThrows(
                        BeanCreationException.class,
                        () ->
                                RouteRegistrarLoader.mergeInto(
                                        result, List.of(bean("com.example.UserController"))),
                        "route for an unknown bean must fail fast, not be dropped");

        assertTrue(
                ex.getMessage().contains("com.example.GhostController"),
                "failure must name the unknown controller: " + ex.getMessage());
    }

    @Test
    void exceptionHandlerForUnknownBeanFailsFast() {
        RouteRegistrarLoader.Result result = new RouteRegistrarLoader.Result();
        result.exceptionHandlers
                .computeIfAbsent("com.example.GhostHandler", k -> new java.util.ArrayList<>())
                .add(
                        new BeanDefinition.ExceptionHandlerEntry(
                                "onError", "java.lang.RuntimeException", 2));

        assertThrows(
                BeanCreationException.class,
                () ->
                        RouteRegistrarLoader.mergeInto(
                                result, List.of(bean("com.example.UserController"))),
                "exception handler for an unknown bean must fail fast, not be dropped");
    }
}
