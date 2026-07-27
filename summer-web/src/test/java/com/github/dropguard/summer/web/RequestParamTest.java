package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        String name = request.getQueryParameter("name");
        assertEquals("john", name);

        String age = request.getQueryParameter("age");
        assertEquals("30", age);

        String active = request.getQueryParameter("active");
        assertEquals("true", active);

        String nonexistent = request.getQueryParameter("nonexistent");
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

        String filter = request.getQueryParameter("filter");
        assertEquals("&=+/?#%", filter);
    }
}
