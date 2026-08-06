package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class RequestParamTest {

    @Test
    public void testRequestParamAnnotation() {
        Request request =
                new Request(
                        HttpMethod.GET,
                        "/api/users",
                        "name=john&age=30&active=true",
                        "text/plain",
                        new byte[0]);

        String name = request.queryParam("name");
        assertEquals("john", name);

        String age = request.queryParam("age");
        assertEquals("30", age);

        String active = request.queryParam("active");
        assertEquals("true", active);

        String nonexistent = request.queryParam("nonexistent");
        assertNull(nonexistent);
    }

    @Test
    public void testRequestParamParsing() {
        Request request =
                new Request(
                        HttpMethod.GET,
                        "/api/search",
                        "q=test+query&page=1&limit=10&sort=asc",
                        "text/plain",
                        new byte[0]);

        var params = request.getQueryParameters();
        assertEquals(4, params.size());
        assertEquals("test query", params.get("q"));
        assertEquals("1", params.get("page"));
        assertEquals("10", params.get("limit"));
        assertEquals("asc", params.get("sort"));
    }

    @Test
    public void testRequestParamWithSpecialCharacters() {
        Request request =
                new Request(
                        HttpMethod.GET,
                        "/api/items",
                        "filter=%26%3D%2B%2F%3F%23%25",
                        "text/plain",
                        new byte[0]);

        String filter = request.queryParam("filter");
        assertEquals("&=+/?#%", filter);
    }

    @Test
    public void duplicateKeysAreFirstWins() {
        Request request =
                new Request(
                        HttpMethod.GET,
                        "/api/search",
                        "page=1&page=2&tag=a&tag=b",
                        "text/plain",
                        new byte[0]);

        // Matches the servlet getParameter convention: the first occurrence wins.
        assertEquals("1", request.queryParam("page"));
        assertEquals("a", request.queryParam("tag"));
        assertEquals(2, request.getQueryParameters().size());
    }

    @Test
    public void malformedEncodingFallsBackToRawValue() {
        Request request =
                new Request(HttpMethod.GET, "/api/search", "q=%zz&ok=1", "text/plain", new byte[0]);

        // Lenient by design: unparseable percent-encoding keeps the raw value,
        // and the rest of the query string still parses.
        assertEquals("%zz", request.queryParam("q"));
        assertEquals("1", request.queryParam("ok"));
    }

    @Test
    public void emptySegmentsAreSkipped() {
        Request request =
                new Request(HttpMethod.GET, "/api/search", "a=1&&b=2", "text/plain", new byte[0]);

        // "&&" (and a leading "&") must not produce an empty-key entry.
        assertEquals(2, request.getQueryParameters().size());
        assertEquals("1", request.queryParam("a"));
        assertEquals("2", request.queryParam("b"));
    }

    @Test
    public void attributesAreAnImmutableView() {
        Request request = new Request(HttpMethod.GET, "/api/x", null, "text/plain", new byte[0]);
        request.setAttribute(RequestAttributes.USER_ID, 1L);

        // Reads go through an unmodifiable view; writes must use the explicit
        // setAttribute / setPathParam surface.
        assertThrows(
                UnsupportedOperationException.class, () -> request.getAttributes().put("k", "v"));
        assertEquals(1L, request.getAttribute(RequestAttributes.USER_ID));
    }
}
