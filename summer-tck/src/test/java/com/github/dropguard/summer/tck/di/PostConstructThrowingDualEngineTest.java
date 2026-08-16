package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.tck.invisible.fixtures.di.ThrowingPostConstructBean;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.api.Test;

/**
 * Dual-engine contract for a {@code @PostConstruct} that throws at runtime (as opposed to the
 * enrichment-time violations in {@link PostConstructViolationTest}): both engines must surface the
 * failure as a {@link BeanCreationException} naming the bean — the runtime engine via its
 * reflective invocation wrapper, the AOT engine via the try/catch emitted around the generated
 * call. Neither engine may leak the raw exception or hide the bean behind a generic compilation
 * error.
 */
public class PostConstructThrowingDualEngineTest {

    @Test
    void throwingPostConstructNamesTheBeanOnBothEngines() {
        for (Engine engine : Engine.values()) {
            BeanCreationException e =
                    assertThrows(
                            BeanCreationException.class,
                            () ->
                                    TestContainer.builder()
                                            .testClass(PostConstructThrowingDualEngineTest.class)
                                            .engine(engine)
                                            .beans(ThrowingPostConstructBean.class)
                                            .build(),
                            engine
                                    + " must wrap a throwing @PostConstruct in"
                                    + " BeanCreationException");
            assertTrue(
                    e.getMessage().contains("Failed to invoke @PostConstruct on bean")
                            && e.getMessage().contains(ThrowingPostConstructBean.class.getName()),
                    engine + " message must name the bean: " + e.getMessage());
        }
    }
}
