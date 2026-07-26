package com.github.dropguard.summer.test.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The universe cache reuses a universe purely on
 * {@link EnvKey#equals}/{@link EnvKey#hashCode} equality — a bug there silently
 * reuses the wrong universe, which manifests as intermittent, unexplained test
 * failures. This test is the compile-time guard against that class of bug: the
 * map key already guarantees the right container is returned on a hit, so the
 * equality contract must be pinned here rather than trusting a runtime check.
 */
class EnvKeyTest {

	private static final EnvKey A = EnvKey.of("p1", List.of("com.X", "com.Y"), "BuilderA");
	private static final EnvKey B = EnvKey.of("p1", List.of("com.X", "com.Y"), "BuilderA");
	private static final EnvKey DIFF_PROFILE = EnvKey.of("p2", List.of("com.X", "com.Y"), "BuilderA");
	private static final EnvKey DIFF_MOCKS = EnvKey.of("p1", List.of("com.X", "com.Z"), "BuilderA");
	private static final EnvKey DIFFERENT_BUILDER = EnvKey.of("p1", List.of("com.X", "com.Y"), "BuilderB");

	@Test
	void equalKeysAreEqualAndShareHash() {
		assertEquals(A, B);
		assertEquals(A.hashCode(), B.hashCode());
	}

	@Test
	void equalKeyIsNotSameInstance() {
		// Equality is value-based, not identity.
		assertNotSame(A, B);
	}

	@Test
	void differentProfileIsNotEqual() {
		assertNotEquals(A, DIFF_PROFILE);
	}

	@Test
	void differentMockSetIsNotEqual() {
		assertNotEquals(A, DIFF_MOCKS);
	}

	@Test
	void differentBuilderIsNotEqual() {
		// The test class (firstBuilder) IS a key dimension: Summer universes are
		// per-test-class, so two classes must never share a cached universe —
		// otherwise a @SummerTest(shouldFail=true) would reuse a passing one.
		assertNotEquals(A, DIFFERENT_BUILDER);
	}

	@Test
	void equalsIsSymmetric() {
		assertEquals(A, B);
		assertEquals(B, A);
	}

	@Test
	void equalsIsReflexiveAndNullSafe() {
		assertEquals(A, A);
		assertNotEquals(A, null);
		assertNotEquals(A, "not an EnvKey");
	}

	@Test
	void hashConsistencyAcrossCalls() {
		assertEquals(A.hashCode(), A.hashCode());
	}

	@Test
	void mockTypeOrderIsSignificant() {
		// mockedTypes is an ordered List, so element order is part of the key.
		// (Callers that build the key from @Mock parameters normalize the order
		// via a sorted set before calling EnvKey.of, which is why order only
		// matters here at the key level — this test pins that contract.)
		EnvKey reordered = EnvKey.of("p1", List.of("com.Y", "com.X"), "BuilderA");
		assertNotEquals(A, reordered);
	}
}
