package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RadixTrie}.
 *
 * <p>Tests the core algorithm: insert, get, match, path parameters, wildcards, priority, and edge
 * cases.
 */
class RadixTrieTest {

    // --- Basic insert and get ---

    @Test
    void shouldInsertAndGetHandler() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users", "user-list");

        assertEquals("user-list", trie.get("/users"));
    }

    @Test
    void shouldReturnNullForMissingPath() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users", "user-list");

        assertNull(trie.get("/posts"));
    }

    @Test
    void shouldRejectDuplicateRouteRegistration() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users", "old");
        assertThrows(
                com.github.dropguard.summer.web.exception.RouteConflictException.class,
                () -> trie.insert("/users", "new"));
        assertEquals("old", trie.get("/users"), "first registration stays intact");
    }

    // --- Static route matching ---

    @Test
    void shouldMatchExactStaticRoute() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users", "user-list");

        RadixTrie.MatchResult<String> result = trie.match("/users");
        assertNotNull(result);
        assertEquals("user-list", result.handler());
        assertTrue(result.params().isEmpty());
    }

    @Test
    void shouldMatchMultiSegmentStaticRoute() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/api/v1/users", "v1-users");

        RadixTrie.MatchResult<String> result = trie.match("/api/v1/users");
        assertNotNull(result);
        assertEquals("v1-users", result.handler());
    }

    @Test
    void shouldNotMatchDifferentPath() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users", "user-list");

        assertNull(trie.match("/posts"));
    }

    // --- Path parameters ---

    @Test
    void shouldExtractSinglePathParam() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users/{id}", "user-detail");

        RadixTrie.MatchResult<String> result = trie.match("/users/42");
        assertNotNull(result);
        assertEquals("user-detail", result.handler());
        assertEquals("42", result.params().get("id"));
    }

    @Test
    void shouldExtractMultiplePathParams() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users/{userId}/posts/{postId}", "user-post");

        RadixTrie.MatchResult<String> result = trie.match("/users/10/posts/20");
        assertNotNull(result);
        assertEquals("user-post", result.handler());
        assertEquals("10", result.params().get("userId"));
        assertEquals("20", result.params().get("postId"));
    }

    @Test
    void shouldExtractPathParamAtStart() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/{tenant}/users", "tenant-users");

        RadixTrie.MatchResult<String> result = trie.match("/acme/users");
        assertNotNull(result);
        assertEquals("tenant-users", result.handler());
        assertEquals("acme", result.params().get("tenant"));
    }

    @Test
    void shouldExtractPathParamAtEnd() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/files/{name}", "file-detail");

        RadixTrie.MatchResult<String> result = trie.match("/files/report.pdf");
        assertNotNull(result);
        assertEquals("file-detail", result.handler());
        assertEquals("report.pdf", result.params().get("name"));
    }

    // --- Wildcards ---

    @Test
    void shouldMatchSingleSegmentWildcard() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/files/*", "any-file");

        RadixTrie.MatchResult<String> result = trie.match("/files/report.pdf");
        assertNotNull(result);
        assertEquals("any-file", result.handler());
    }

    @Test
    void shouldMatchCatchAllWildcard() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/static/**", "static-files");

        RadixTrie.MatchResult<String> result = trie.match("/static/css/style.css");
        assertNotNull(result);
        assertEquals("static-files", result.handler());
    }

    @Test
    void shouldMatchCatchAllAtRoot() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/**", "catch-all");

        RadixTrie.MatchResult<String> result = trie.match("/anything/at/all");
        assertNotNull(result);
        assertEquals("catch-all", result.handler());

        RadixTrie.MatchResult<String> rootResult = trie.match("/");
        assertNotNull(rootResult);
        assertEquals("catch-all", rootResult.handler());

        RadixTrie.MatchResult<String> emptyResult = trie.match("");
        assertNotNull(emptyResult);
        assertEquals("catch-all", emptyResult.handler());
    }

    @Test
    void shouldPreferExactRootOverCatchAll() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/", "exact-root");
        trie.insert("/**", "catch-all");

        assertEquals("exact-root", trie.match("/").handler());
        assertEquals("exact-root", trie.match("").handler());
        assertEquals("catch-all", trie.match("/other").handler());
    }

    @Test
    void shouldRejectMultiSegmentWildcardInMiddleOfPath() {
        RadixTrie<String> trie = new RadixTrie<>();
        assertThrows(IllegalArgumentException.class, () -> trie.insert("/a/**/b", "invalid"));
        assertThrows(IllegalArgumentException.class, () -> trie.insert("/**/b", "invalid"));
    }

    // --- Root path ---

    @Test
    void shouldMatchRootPath() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/", "root");

        RadixTrie.MatchResult<String> result = trie.match("/");
        assertNotNull(result);
        assertEquals("root", result.handler());
        assertTrue(result.params().isEmpty());
    }

    @Test
    void shouldMatchEmptyPathAsRoot() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/", "root");

        RadixTrie.MatchResult<String> result = trie.match("");
        assertNotNull(result);
        assertEquals("root", result.handler());
    }

    @Test
    void shouldMatchNullPathAsRoot() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/", "root");

        RadixTrie.MatchResult<String> result = trie.match(null);
        assertNotNull(result);
        assertEquals("root", result.handler());
    }

    @Test
    void shouldReturnNullForRootWhenNotInserted() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users", "user-list");

        assertNull(trie.match("/"));
    }

    // --- Priority (static > param > wildcard) ---

    @Test
    void shouldPreferStaticOverParam() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users/me", "current-user");
        trie.insert("/users/{id}", "user-by-id");

        RadixTrie.MatchResult<String> result = trie.match("/users/me");
        assertNotNull(result);
        assertEquals("current-user", result.handler());
    }

    @Test
    void shouldPreferParamOverWildcard() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users/{id}", "user-by-id");
        trie.insert("/users/*", "any-user");

        RadixTrie.MatchResult<String> result = trie.match("/users/42");
        assertNotNull(result);
        assertEquals("user-by-id", result.handler());
        assertEquals("42", result.params().get("id"));
    }

    // --- Edge cases ---

    @Test
    void shouldNormalizePath() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users", "user-list");

        // Should match with or without trailing slash
        assertNotNull(trie.get("users"));
        assertNotNull(trie.get("/users/"));
    }

    @Test
    void shouldHandleMultipleRoutes() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users", "user-list");
        trie.insert("/users/{id}", "user-detail");
        trie.insert("/posts", "post-list");
        trie.insert("/posts/{id}", "post-detail");

        assertEquals("user-list", trie.match("/users").handler());
        assertEquals("user-detail", trie.match("/users/1").handler());
        assertEquals("post-list", trie.match("/posts").handler());
        assertEquals("post-detail", trie.match("/posts/1").handler());
    }

    @Test
    void shouldHandleDeepNestedRoutes() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/a/b/c/d/e", "deep");

        RadixTrie.MatchResult<String> result = trie.match("/a/b/c/d/e");
        assertNotNull(result);
        assertEquals("deep", result.handler());
    }

    @Test
    void shouldReturnNullForPartialMatch() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users/{id}", "user-detail");

        // /users should not match /users/{id}
        assertNull(trie.match("/users"));
    }

    @Test
    void shouldReturnNullForLongerPath() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users", "user-list");

        // /users/extra should not match /users
        assertNull(trie.match("/users/extra"));
    }

    // --- Edge cases (Hardcore tests added) ---

    @Test
    void shouldThrowExceptionOnParamConflict() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/users/{id}", "user-by-id");

        // Attempting to insert a different parameter name at the same position
        com.github.dropguard.summer.web.exception.RouteConflictException ex =
                assertThrows(
                        com.github.dropguard.summer.web.exception.RouteConflictException.class,
                        () -> trie.insert("/users/{userId}", "user-by-userid"));

        assertTrue(ex.getMessage().contains("/users/{userId}"));
    }

    @Test
    void shouldMatchCatchAllWhenPathEndsExactlyAtParentNode() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/api/**", "api-catch-all");

        // The path "/api" exactly matches the parent node.
        // Since the parent node has no handler, it should fallback to the ** child.
        RadixTrie.MatchResult<String> result = trie.match("/api");
        assertNotNull(result);
        assertEquals("api-catch-all", result.handler());
    }

    @Test
    void shouldNotFallbackToCatchAllIfParentHasExactHandler() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/api", "api-exact");
        trie.insert("/api/**", "api-catch-all");

        // Should prefer exact handler over catch-all fallback
        RadixTrie.MatchResult<String> result = trie.match("/api");
        assertNotNull(result);
        assertEquals("api-exact", result.handler());
    }

    // --- Backtracking state hygiene (contract: catch-all matches carry only
    // bindings that legitimately led INTO the catch-all, never debris from a
    // dead-ended more-specific branch) ---

    @Test
    void catchAllFallbackMustNotLeakParamsFromDeadEndedBranch() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/files/**", "catch-all");
        trie.insert("/files/{name}/meta", "meta");

        RadixTrie.MatchResult<String> result = trie.match("/files/a/b/c");
        assertNotNull(result);
        assertEquals(
                "catch-all",
                result.handler(),
                "the specific branch dead-ends, so the catch-all must serve");
        assertTrue(
                result.params().isEmpty(),
                "params bound inside the dead-ended {name} branch must not leak "
                        + "into the catch-all result");
    }

    @Test
    void deeperMultiParamDeadEndKeepsPrefixBindingsDropsBranchDebris() {
        RadixTrie<String> trie = new RadixTrie<>();
        // The catch-all pattern itself declares {a}: it applies to everything the
        // pattern matches, so a=1 is a LEGITIMATE prefix binding. b was bound only
        // inside the dead-ended /{b}/end branch and must not survive.
        trie.insert("/u/{a}/p/{b}/end", "specific");
        trie.insert("/u/{a}/p/**", "catch-all");

        RadixTrie.MatchResult<String> result = trie.match("/u/1/p/2/x/y/z");
        assertNotNull(result);
        assertEquals("catch-all", result.handler());
        assertEquals(
                java.util.Map.of("a", "1"),
                result.params(),
                "prefix bindings on the path INTO the catch-all are kept; bindings "
                        + "made inside the dead-ended branch are dropped");
    }

    @Test
    void paramsBoundOnThePathIntoTheCatchAllAreKept() {
        // /files/{dir}/** : the binding happens ON the successful route into the
        // catch-all scope, so it is legitimate.
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/files/{dir}/**", "dir-files");

        RadixTrie.MatchResult<String> result = trie.match("/files/docs/a/b");
        assertNotNull(result);
        assertEquals("dir-files", result.handler());
        assertEquals(java.util.Map.of("dir", "docs"), result.params());
    }

    // --- Path segment decoding contract (RFC 3986: percent-decoding only,
    // literal '+' preserved — query strings are a different context) ---

    @Test
    void plusSignInPathSegmentIsLiteral() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/u/{email}", "user");

        RadixTrie.MatchResult<String> result = trie.match("/u/a+b@x.com");
        assertNotNull(result);
        assertEquals("user", result.handler());
        assertEquals(
                "a+b@x.com",
                result.params().get("email"),
                "'+' is a legal literal character in URI paths (RFC 3986) and must "
                        + "not be decoded as a space");
    }

    @Test
    void percentEscapesAreStillDecoded() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/u/{email}", "user");

        RadixTrie.MatchResult<String> result = trie.match("/u/a%40b.com");
        assertEquals("a@b.com", result.params().get("email"));
    }

    @Test
    void invalidPercentEscapeIsRejectedTyped() {
        RadixTrie<String> trie = new RadixTrie<>();
        trie.insert("/u/{email}", "user");

        com.github.dropguard.summer.web.exception.SummerWebException ex =
                assertThrows(
                        com.github.dropguard.summer.web.exception.SummerWebException.class,
                        () -> trie.match("/u/%zz"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode());
    }
}
