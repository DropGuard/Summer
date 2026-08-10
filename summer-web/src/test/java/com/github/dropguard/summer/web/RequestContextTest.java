package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RequestContext}: a request-backed context exposes the request; a userId-only
 * context (non-HTTP flows via {@link RequestContextHolder#set(Long)}) must fail loudly on the
 * request-backed accessors instead of fabricating or silently degrading to an empty request.
 */
class RequestContextTest {

    @Test
    void requestBackedContextExposesRequestAndAttributes() {
        Request request = new Request(HttpMethod.GET, "/hello", null, null, null);
        RequestContext ctx = new RequestContext(request, 42L);

        assertEquals(request, ctx.request());
        assertEquals(42L, ctx.userId());
        assertEquals(request.getAttributes(), ctx.attributes());
    }

    @Test
    void userIdOnlyContextCarriesUserIdButNoRequest() {
        RequestContext ctx = new RequestContext(42L);

        assertEquals(42L, ctx.userId());
        assertThrows(IllegalStateException.class, ctx::request, "no HTTP request here");
        assertThrows(IllegalStateException.class, ctx::attributes, "attributes are request-backed");
        assertThrows(
                IllegalStateException.class,
                () -> ctx.attribute(RequestAttributes.USER_ID),
                "attribute lookup is request-backed");
    }
}
