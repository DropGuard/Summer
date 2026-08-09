package com.github.dropguard.summer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.github.dropguard.summer.core.Engine;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The universe cache reuses a universe purely on {@link UniverseKey#equals}/{@link
 * UniverseKey#hashCode} equality — a bug there silently reuses the wrong universe, which manifests
 * as intermittent, unexplained test failures. This test is the compile-time guard against that
 * class of bug: the map key already guarantees the right container is returned on a hit, so the
 * equality contract must be pinned here rather than trusting a runtime check.
 */
class UniverseKeyTest {

    private static final UniverseKey A =
            UniverseKey.of("p1", List.of("com.X", "com.Y"), Engine.RUNTIME, "BuilderA");
    private static final UniverseKey B =
            UniverseKey.of("p1", List.of("com.X", "com.Y"), Engine.RUNTIME, "BuilderA");
    private static final UniverseKey DIFF_PROFILE =
            UniverseKey.of("p2", List.of("com.X", "com.Y"), Engine.RUNTIME, "BuilderA");
    private static final UniverseKey DIFF_MOCKS =
            UniverseKey.of("p1", List.of("com.X", "com.Z"), Engine.RUNTIME, "BuilderA");
    private static final UniverseKey DIFF_ENGINE =
            UniverseKey.of("p1", List.of("com.X", "com.Y"), Engine.AOT, "BuilderA");
    private static final UniverseKey DIFFERENT_BUILDER =
            UniverseKey.of("p1", List.of("com.X", "com.Y"), Engine.RUNTIME, "BuilderB");

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
    void differentEngineIsNotEqual() {
        // Runtime and AOT build different containers — the AOT branch must never
        // reuse the RUNTIME container, or the AOT engine is never actually tested.
        assertNotEquals(A, DIFF_ENGINE);
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
        assertNotEquals(A, "not an UniverseKey");
    }

    @Test
    void hashConsistencyAcrossCalls() {
        assertEquals(A.hashCode(), A.hashCode());
    }

    @Test
    void mockTypeOrderIsSignificant() {
        // mockedTypes is an ordered List, so element order is part of the key.
        // (Callers that build the key from @Mock parameters normalize the order
        // via a sorted set before calling UniverseKey.of, which is why order only
        // matters here at the key level — this test pins that contract.)
        UniverseKey reordered =
                UniverseKey.of("p1", List.of("com.Y", "com.X"), Engine.RUNTIME, "BuilderA");
        assertNotEquals(A, reordered);
    }
}
