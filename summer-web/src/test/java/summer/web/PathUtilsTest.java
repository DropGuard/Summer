package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PathUtils}.
 *
 * <p>
 * Tests path normalization and combination edge cases.
 * </p>
 */
class PathUtilsTest {

	// --- normalizePath ---

	@Test
	void shouldNormalizeNullToRoot() {
		assertEquals("/", PathUtils.normalizePath(null));
	}

	@Test
	void shouldNormalizeEmptyToRoot() {
		assertEquals("/", PathUtils.normalizePath(""));
	}

	@Test
	void shouldAddLeadingSlash() {
		assertEquals("/users", PathUtils.normalizePath("users"));
	}

	@Test
	void shouldKeepExistingLeadingSlash() {
		assertEquals("/users", PathUtils.normalizePath("/users"));
	}

	@Test
	void shouldRemoveTrailingSlash() {
		assertEquals("/users", PathUtils.normalizePath("/users/"));
	}

	@Test
	void shouldKeepRootTrailingSlash() {
		assertEquals("/", PathUtils.normalizePath("/"));
	}

	@Test
	void shouldCollapseMultipleSlashes() {
		assertEquals("/users", PathUtils.normalizePath("//users///"));
	}

	@Test
	void shouldNormalizeComplexPath() {
		assertEquals("/api/v1/users", PathUtils.normalizePath("///api//v1///users/"));
	}

	// --- combinePaths ---

	@Test
	void shouldCombineWithEmptyBase() {
		assertEquals("/users", PathUtils.combinePaths("", "/users"));
	}

	@Test
	void shouldCombineWithEmptyMethod() {
		assertEquals("/api", PathUtils.combinePaths("/api", ""));
	}

	@Test
	void shouldCombineBaseWithSlashAndMethodWithSlash() {
		assertEquals("/api/users", PathUtils.combinePaths("/api/", "/users"));
	}

	@Test
	void shouldCombineBaseWithoutSlashAndMethodWithoutSlash() {
		assertEquals("/api/users", PathUtils.combinePaths("/api", "users"));
	}

	@Test
	void shouldCombineBaseWithSlashAndMethodWithoutSlash() {
		assertEquals("/api/users", PathUtils.combinePaths("/api/", "users"));
	}

	@Test
	void shouldCombineBaseWithoutSlashAndMethodWithSlash() {
		assertEquals("/api/users", PathUtils.combinePaths("/api", "/users"));
	}

	@Test
	void shouldNormalizeBothPathsWhenCombining() {
		assertEquals("/api/v1/users", PathUtils.combinePaths("//api//", "//v1//users//"));
	}
}
