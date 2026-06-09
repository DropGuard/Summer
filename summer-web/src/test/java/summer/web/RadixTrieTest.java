package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RadixTrie}.
 *
 * <p>
 * Tests the core algorithm: insert, get, match, path parameters, wildcards,
 * priority, and edge cases.
 * </p>
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
	void shouldOverwriteHandlerForSamePath() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/users", "old");
		trie.insert("/users", "new");

		assertEquals("new", trie.get("/users"));
	}

	// --- Static route matching ---

	@Test
	void shouldMatchExactStaticRoute() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/users", "user-list");

		RadixTrie.MatchResult<String> result = trie.match("/users".getBytes());
		assertNotNull(result);
		assertEquals("user-list", result.handler());
		assertTrue(result.params().isEmpty());
	}

	@Test
	void shouldMatchMultiSegmentStaticRoute() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/api/v1/users", "v1-users");

		RadixTrie.MatchResult<String> result = trie.match("/api/v1/users".getBytes());
		assertNotNull(result);
		assertEquals("v1-users", result.handler());
	}

	@Test
	void shouldNotMatchDifferentPath() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/users", "user-list");

		assertNull(trie.match("/posts".getBytes()));
	}

	// --- Path parameters ---

	@Test
	void shouldExtractSinglePathParam() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/users/{id}", "user-detail");

		RadixTrie.MatchResult<String> result = trie.match("/users/42".getBytes());
		assertNotNull(result);
		assertEquals("user-detail", result.handler());
		assertEquals("42", result.params().get("id"));
	}

	@Test
	void shouldExtractMultiplePathParams() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/users/{userId}/posts/{postId}", "user-post");

		RadixTrie.MatchResult<String> result = trie.match("/users/10/posts/20".getBytes());
		assertNotNull(result);
		assertEquals("user-post", result.handler());
		assertEquals("10", result.params().get("userId"));
		assertEquals("20", result.params().get("postId"));
	}

	@Test
	void shouldExtractPathParamAtStart() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/{tenant}/users", "tenant-users");

		RadixTrie.MatchResult<String> result = trie.match("/acme/users".getBytes());
		assertNotNull(result);
		assertEquals("tenant-users", result.handler());
		assertEquals("acme", result.params().get("tenant"));
	}

	@Test
	void shouldExtractPathParamAtEnd() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/files/{name}", "file-detail");

		RadixTrie.MatchResult<String> result = trie.match("/files/report.pdf".getBytes());
		assertNotNull(result);
		assertEquals("file-detail", result.handler());
		assertEquals("report.pdf", result.params().get("name"));
	}

	// --- Wildcards ---

	@Test
	void shouldMatchSingleSegmentWildcard() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/files/*", "any-file");

		RadixTrie.MatchResult<String> result = trie.match("/files/report.pdf".getBytes());
		assertNotNull(result);
		assertEquals("any-file", result.handler());
	}

	@Test
	void shouldMatchCatchAllWildcard() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/static/**", "static-files");

		RadixTrie.MatchResult<String> result = trie.match("/static/css/style.css".getBytes());
		assertNotNull(result);
		assertEquals("static-files", result.handler());
	}

	@Test
	void shouldMatchCatchAllAtRoot() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/**", "catch-all");

		RadixTrie.MatchResult<String> result = trie.match("/anything/at/all".getBytes());
		assertNotNull(result);
		assertEquals("catch-all", result.handler());
	}

	// --- Root path ---

	@Test
	void shouldMatchRootPath() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/", "root");

		RadixTrie.MatchResult<String> result = trie.match("/".getBytes());
		assertNotNull(result);
		assertEquals("root", result.handler());
		assertTrue(result.params().isEmpty());
	}

	@Test
	void shouldMatchEmptyPathAsRoot() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/", "root");

		RadixTrie.MatchResult<String> result = trie.match(new byte[0]);
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

		assertNull(trie.match("/".getBytes()));
	}

	// --- Priority (static > param > wildcard) ---

	@Test
	void shouldPreferStaticOverParam() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/users/me", "current-user");
		trie.insert("/users/{id}", "user-by-id");

		RadixTrie.MatchResult<String> result = trie.match("/users/me".getBytes());
		assertNotNull(result);
		assertEquals("current-user", result.handler());
	}

	@Test
	void shouldPreferParamOverWildcard() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/users/{id}", "user-by-id");
		trie.insert("/users/*", "any-user");

		RadixTrie.MatchResult<String> result = trie.match("/users/42".getBytes());
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

		assertEquals("user-list", trie.match("/users".getBytes()).handler());
		assertEquals("user-detail", trie.match("/users/1".getBytes()).handler());
		assertEquals("post-list", trie.match("/posts".getBytes()).handler());
		assertEquals("post-detail", trie.match("/posts/1".getBytes()).handler());
	}

	@Test
	void shouldHandleDeepNestedRoutes() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/a/b/c/d/e", "deep");

		RadixTrie.MatchResult<String> result = trie.match("/a/b/c/d/e".getBytes());
		assertNotNull(result);
		assertEquals("deep", result.handler());
	}

	@Test
	void shouldReturnNullForPartialMatch() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/users/{id}", "user-detail");

		// /users should not match /users/{id}
		assertNull(trie.match("/users".getBytes()));
	}

	@Test
	void shouldReturnNullForLongerPath() {
		RadixTrie<String> trie = new RadixTrie<>();
		trie.insert("/users", "user-list");

		// /users/extra should not match /users
		assertNull(trie.match("/users/extra".getBytes()));
	}
}
