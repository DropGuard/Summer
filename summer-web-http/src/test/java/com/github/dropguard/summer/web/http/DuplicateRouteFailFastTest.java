package com.github.dropguard.summer.web.http;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.exception.RouteConflictException;
import org.junit.jupiter.api.Test;

/**
 * Pins the startup contract: registering the same METHOD+path twice must fail router build (i.e.,
 * application boot), not silently let the later registration win.
 */
class DuplicateRouteFailFastTest {

    @Test
    void mapRouterRejectsDuplicateMethodPath() {
        var route = new HttpRouter.Builder.Route(HttpMethod.GET, "/users", ctx -> {});
        var ex =
                assertThrows(
                        RouteConflictException.class,
                        () -> new MapRouter(java.util.List.of(route, route)));
        assertTrue(ex.getMessage().contains("GET /users"));
    }

    @Test
    void builderRejectsDuplicateRegistrationAcrossGroups() {
        var ex =
                assertThrows(
                        RouteConflictException.class,
                        () ->
                                new HttpRouter.Builder(RadixTreeHttpRouter::new)
                                        .get("/dup", ctx -> {})
                                        .get("/dup", ctx -> {})
                                        .build());
        assertTrue(ex.getMessage().contains("/dup"));
    }
}
