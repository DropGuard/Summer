package com.github.dropguard.summer.web.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Request;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Both HTTP router implementations must set {@code HttpContext.ResponseState.MATCHED} exactly when
 * a route handler is dispatched — the server layer relies on it to answer a request that ends with
 * no status: 404 when no route matched, 500 when a matched handler wrote nothing.
 */
class RouterResponseStateTest {

    @Test
    void radixRouterMarksMatchedOnHitAndUnsetOnMiss() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET,
                                        "/hello",
                                        ctx -> ctx.text(HttpStatus.OK, "world"))));

        HttpContext hit = new HttpContext(new Request(HttpMethod.GET, "/hello", null, null, null));
        router.route(hit);
        assertEquals(HttpContext.ResponseState.MATCHED, hit.responseState());
        assertEquals(HttpStatus.OK, hit.status());

        HttpContext miss = new HttpContext(new Request(HttpMethod.GET, "/nope", null, null, null));
        router.route(miss);
        assertEquals(HttpContext.ResponseState.UNSET, miss.responseState());
        assertNull(miss.status());
    }

    @Test
    void radixRouterMatchedHandlerThatWritesNothingLeavesStatusNull() throws Exception {
        RadixTreeHttpRouter router =
                new RadixTreeHttpRouter(
                        List.of(
                                new HttpRouter.Builder.Route(
                                        HttpMethod.GET, "/silent", ctx -> {})));

        HttpContext ctx = new HttpContext(new Request(HttpMethod.GET, "/silent", null, null, null));
        router.route(ctx);
        assertEquals(HttpContext.ResponseState.MATCHED, ctx.responseState());
        assertNull(
                ctx.status(),
                "a silent handler must leave the status unset for the server to flag");
    }

    @Test
    void mapRouterMarksMatchedOnHitAndUnsetOnMiss() throws Exception {
        HttpRouter router =
                new HttpRouter.Builder(MapRouter::new)
                        .get("/hello", ctx -> ctx.text(HttpStatus.OK, "world"))
                        .build();

        HttpContext hit = new HttpContext(new Request(HttpMethod.GET, "/hello", null, null, null));
        router.route(hit);
        assertEquals(HttpContext.ResponseState.MATCHED, hit.responseState());
        assertEquals(HttpStatus.OK, hit.status());

        HttpContext miss = new HttpContext(new Request(HttpMethod.GET, "/nope", null, null, null));
        router.route(miss);
        assertEquals(HttpContext.ResponseState.UNSET, miss.responseState());
        assertNull(miss.status());
    }

    @Test
    void mapRouterMatchedHandlerThatWritesNothingLeavesStatusNull() throws Exception {
        HttpRouter router =
                new HttpRouter.Builder(MapRouter::new).get("/silent", ctx -> {}).build();

        HttpContext ctx = new HttpContext(new Request(HttpMethod.GET, "/silent", null, null, null));
        router.route(ctx);
        assertEquals(HttpContext.ResponseState.MATCHED, ctx.responseState());
        assertNull(ctx.status());
    }
}
