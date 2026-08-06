package com.github.dropguard.summer.runtime.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Request;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebContextTest {

    @Test
    void requestAccess() {
        Request req = new Request(HttpMethod.GET, "/test", "q=1", null, null);
        HttpContext ctx = new HttpContext(req);
        assertEquals(HttpMethod.GET, ctx.method());
        assertEquals("/test", ctx.path());
        assertEquals("1", ctx.queryParam("q"));
    }

    @Test
    void okJson() {
        Request req = new Request(HttpMethod.GET, "/test", null, null, null);
        HttpContext ctx = new HttpContext(req);
        ctx.ok("test-data");
        assertEquals(HttpStatus.OK, ctx.status());
        assertNotNull(ctx.resultObject());
    }

    @Test
    void textResponse() {
        Request req = new Request(HttpMethod.GET, "/test", null, null, null);
        HttpContext ctx = new HttpContext(req);
        ctx.text(HttpStatus.OK, "hello");
        assertEquals(HttpStatus.OK, ctx.status());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), ctx.body());
    }

    @Test
    void errorResponse() {
        Request req = new Request(HttpMethod.GET, "/test", null, null, null);
        HttpContext ctx = new HttpContext(req);
        ctx.error(new RuntimeException("boom"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ctx.status());
    }

    @Test
    void setHeader() {
        Request req = new Request(HttpMethod.GET, "/test", null, null, null);
        HttpContext ctx = new HttpContext(req);
        ctx.setHeader("X-Custom", "value");
        assertEquals("value", ctx.headers().get("X-Custom"));
    }
}
