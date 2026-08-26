package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // --- Namespace contract: path params and typed attributes are isolated ---

    @Test
    public void routeParamAndSameNameAttributeCoexistWithoutTypeCorruption() {
        Request request = new Request(HttpMethod.GET, "/users/7", null, null, new byte[0]);
        // Simulates the collision that used to be a ClassCastException: routing
        // binds {userId}="7" (String), then middleware stores USER_ID as Long.
        request.setPathParam("userId", "7");
        request.setAttribute(RequestAttributes.USER_ID, 42L);

        assertEquals(42L, (Long) request.getAttribute(RequestAttributes.USER_ID));
        assertEquals("7", request.pathParam("userId"));
        // Order independence: attribute first, param second.
        Request flipped = new Request(HttpMethod.GET, "/users/7", null, null, new byte[0]);
        flipped.setAttribute(RequestAttributes.USER_ID, 42L);
        flipped.setPathParam("userId", "7");
        assertEquals(42L, (Long) flipped.getAttribute(RequestAttributes.USER_ID));
        assertEquals("7", flipped.pathParam("userId"));
    }

    @Test
    public void sameNameLastWriteWinsWithinOneNamespaceOnly() {
        Request request = new Request(HttpMethod.GET, "/u/x", null, null, new byte[0]);
        request.setPathParam("id", "first");
        request.setPathParam("id", "second");
        assertEquals("second", request.pathParam("id"));

        request.setAttribute(RequestAttributes.USER_ID, 1L);
        request.setAttribute(RequestAttributes.USER_ID, 2L);
        assertEquals(2L, (Long) request.getAttribute(RequestAttributes.USER_ID));
    }

    @Test
    public void attributesViewExcludesPathParams() {
        Request request = new Request(HttpMethod.GET, "/u/9", null, null, new byte[0]);
        // Distinct names on purpose: with a colliding name the attribute itself
        // would legitimately appear in the view and mask what we're asserting.
        request.setPathParam("resourceId", "9");
        request.setAttribute(RequestAttributes.USER_ID, 5L);

        assertTrue(request.getAttributes().containsKey(RequestAttributes.USER_ID.name()));
        assertFalse(
                request.getAttributes().containsKey("resourceId"),
                "the attributes view is the typed-attribute namespace only");
    }

    @Test
    public void missingEntriesReturnNull() {
        Request request = new Request(HttpMethod.GET, "/", null, null, new byte[0]);
        assertNull(request.pathParam("anything"));
        assertNull(request.getAttribute(RequestAttributes.USER_ID));
    }
}
