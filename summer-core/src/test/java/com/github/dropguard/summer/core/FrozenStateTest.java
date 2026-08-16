package com.github.dropguard.summer.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Locks the FrozenState guard contract: mutable until freeze(), read-only afterwards. */
class FrozenStateTest {

    @Test
    void mutableBeforeFreeze() {
        FrozenState state = new FrozenState();

        assertFalse(state.isFrozen());
        state.ensureMutable();
        state.ensureMutable("register");
    }

    @Test
    void freezeIsIdempotentAndGuardsMutations() {
        FrozenState state = new FrozenState();

        state.freeze();
        state.freeze(); // idempotent (a proxied bean freezes more than once)

        assertTrue(state.isFrozen());
        assertThrows(IllegalStateException.class, state::ensureMutable);
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> state.ensureMutable("register"));
        assertTrue(
                e.getMessage().contains("register"),
                "operation name must surface: " + e.getMessage());
    }
}
