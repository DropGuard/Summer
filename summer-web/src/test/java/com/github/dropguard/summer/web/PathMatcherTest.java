package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PathMatcher}.
 *
 * <p>Tests pattern compilation and matching for {param}, *, ** wildcards.
 */
class PathMatcherTest {

    // --- Static routes ---

    @Test
    void shouldMatchExactStaticRoute() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/users");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/users");

        assertNotNull(params);
        assertTrue(params.isEmpty());
    }

    @Test
    void shouldMatchMultiSegmentStaticRoute() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/api/v1/users");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/api/v1/users");

        assertNotNull(params);
        assertTrue(params.isEmpty());
    }

    @Test
    void shouldNotMatchDifferentPath() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/users");
        assertNull(PathMatcher.matchPattern(entry, "/posts"));
    }

    @Test
    void shouldNotMatchPartialPath() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/users");
        assertNull(PathMatcher.matchPattern(entry, "/users/extra"));
    }

    // --- Path parameters ---

    @Test
    void shouldExtractSingleParam() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/users/{id}");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/users/42");

        assertNotNull(params);
        assertEquals("42", params.get("id"));
    }

    @Test
    void shouldExtractMultipleParams() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/users/{userId}/posts/{postId}");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/users/10/posts/20");

        assertNotNull(params);
        assertEquals("10", params.get("userId"));
        assertEquals("20", params.get("postId"));
    }

    @Test
    void shouldExtractParamAtStart() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/{tenant}/users");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/acme/users");

        assertNotNull(params);
        assertEquals("acme", params.get("tenant"));
    }

    @Test
    void shouldExtractParamAtEnd() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/files/{name}");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/files/report.pdf");

        assertNotNull(params);
        assertEquals("report.pdf", params.get("name"));
    }

    // --- Wildcards ---

    @Test
    void shouldMatchSingleSegmentWildcard() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/files/*");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/files/report.pdf");

        assertNotNull(params);
        assertTrue(params.isEmpty());
    }

    @Test
    void shouldMatchCatchAllWildcard() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/static/**");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/static/css/style.css");

        assertNotNull(params);
    }

    @Test
    void shouldMatchCatchAllAtRoot() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/**");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/anything/at/all");

        assertNotNull(params);
    }

    // --- Edge cases ---

    @Test
    void shouldNormalizePath() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("users");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/users");

        assertNotNull(params);
    }

    @Test
    void shouldHandleTrailingSlash() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/users/");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/users");

        assertNotNull(params);
    }

    @Test
    void shouldHandleRootPath() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/");

        assertNotNull(params);
    }

    @Test
    void shouldNotMatchRootWhenPatternHasSegments() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/users");
        assertNull(PathMatcher.matchPattern(entry, "/"));
    }

    // --- Parameter decoding ---

    @Test
    void shouldDecodeUrlEncodedParam() {
        PathMatcher.RouteEntry entry = PathMatcher.parsePath("/files/{name}");
        Map<String, String> params = PathMatcher.matchPattern(entry, "/files/hello%20world");

        assertNotNull(params);
        assertEquals("hello world", params.get("name"));
    }
}
