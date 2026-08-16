package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.tck.invisible.fixtures.di.NonPublicPostConstructBean;
import com.github.dropguard.summer.tck.invisible.fixtures.di.ParamPostConstructBean;
import com.github.dropguard.summer.tck.invisible.fixtures.di.StaticPostConstructBean;
import com.github.dropguard.summer.tck.invisible.fixtures.di.TwoPostConstructBean;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.api.Test;

/**
 * Fail-fast contract for {@code @PostConstruct} violations — both engines must reject the build at
 * enrichment time, before any instantiation, with the offending rule in the message.
 */
public class PostConstructViolationTest {

    @Test
    void twoPostConstructMethodsFail() {
        assertBuildFails(TwoPostConstructBean.class, "at most one @PostConstruct");
    }

    @Test
    void staticPostConstructFails() {
        assertBuildFails(StaticPostConstructBean.class, "must not be static");
    }

    @Test
    void parameterizedPostConstructFails() {
        assertBuildFails(ParamPostConstructBean.class, "must not declare parameters");
    }

    @Test
    void nonPublicPostConstructFails() {
        assertBuildFails(NonPublicPostConstructBean.class, "must be public");
    }

    private static void assertBuildFails(Class<?> bean, String messageFragment) {
        for (Engine engine : Engine.values()) {
            BeanCreationException e =
                    assertThrows(
                            BeanCreationException.class,
                            () ->
                                    TestContainer.builder()
                                            .testClass(PostConstructViolationTest.class)
                                            .engine(engine)
                                            .beans(bean)
                                            .build(),
                            engine + " invocation must reject the @PostConstruct violation");
            assertTrue(
                    e.getMessage().contains(messageFragment),
                    engine
                            + " invocation: expected '"
                            + messageFragment
                            + "' in: "
                            + e.getMessage());
        }
    }
}
