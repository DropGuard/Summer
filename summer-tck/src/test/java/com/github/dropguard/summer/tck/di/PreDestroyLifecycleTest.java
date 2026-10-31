package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.fixtures.lifecycle.DuckTypedCloseBean;
import com.github.dropguard.summer.fixtures.lifecycle.LifecycleEvents;
import com.github.dropguard.summer.fixtures.lifecycle.PreDestroyInner;
import com.github.dropguard.summer.fixtures.lifecycle.PreDestroyOuter;
import com.github.dropguard.summer.test.TestContainer;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

/**
 * The shutdown contract: {@code @PreDestroy} runs per bean BEFORE the conventional {@code
 * AutoCloseable close()}, teardown follows reverse creation order (a dependent's destroy runs while
 * its dependency is still alive), and {@code @PreDestroy} alone — without {@code AutoCloseable} —
 * is sufficient. Pinned on BOTH engines via narrow containers that the test closes itself.
 *
 * <p>Also pins the duck-typed {@code close()} warning: a public no-arg {@code close()} without
 * {@code AutoCloseable} is never called at shutdown, and discovery says so loudly instead of
 * staying silent.
 */
class PreDestroyLifecycleTest {

    @ParameterizedTest
    @EnumSource(Engine.class)
    void preDestroyRunsBeforeCloseInReverseCreationOrder(Engine engine) throws Exception {
        LifecycleEvents.EVENTS.clear();
        BeanContainer container =
                TestContainer.builder()
                        .testClass(getClass())
                        .engine(engine)
                        .beans(PreDestroyInner.class, PreDestroyOuter.class)
                        .build();

        // No bean is torn down while the container is alive.
        assertTrue(LifecycleEvents.EVENTS.isEmpty());
        assertTrue(container.getBean(PreDestroyInner.class).isAlive());

        container.close();

        assertEquals(
                List.of("outer.preDestroy:innerAlive=true", "inner.preDestroy", "inner.close"),
                LifecycleEvents.EVENTS,
                "teardown must be reverse creation order, @PreDestroy before close(), and the"
                        + " dependency must still be alive when the dependent's destroy runs");
    }

    @Test
    void preDestroyWithoutAutoCloseableIsSufficient() throws Exception {
        LifecycleEvents.EVENTS.clear();
        BeanContainer container =
                TestContainer.builder()
                        .testClass(getClass())
                        .beans(PreDestroyOuter.class, PreDestroyInner.class)
                        .build();
        container.close();

        assertTrue(
                LifecycleEvents.EVENTS.contains("outer.preDestroy:innerAlive=true"),
                "a bean with only @PreDestroy (no AutoCloseable) must still be torn down");
    }

    @ParameterizedTest
    @EnumSource(Engine.class)
    void duckTypedCloseIsWarnedAboutButNeverCalled(Engine engine) throws Exception {
        Logger enrichmentLogger =
                (Logger)
                        LoggerFactory.getLogger(
                                "com.github.dropguard.summer.engine.BeanEnrichment");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        enrichmentLogger.addAppender(appender);
        try {
            BeanContainer container =
                    TestContainer.builder()
                            .testClass(getClass())
                            .engine(engine)
                            .beans(DuckTypedCloseBean.class)
                            .build();
            container.close();

            assertFalse(
                    container.getBean(DuckTypedCloseBean.class).isClosed(),
                    "a duck-typed close() must never be called by the container");
            List<ILoggingEvent> warnings =
                    appender.list.stream()
                            .filter(e -> e.getLevel() == Level.WARN)
                            .filter(e -> e.getFormattedMessage().contains("will NOT be closed"))
                            .collect(Collectors.toList());
            assertTrue(
                    warnings.stream()
                            .anyMatch(e -> e.getFormattedMessage().contains("DuckTypedCloseBean")),
                    "discovery must warn that the duck-typed close() will not run");
        } finally {
            enrichmentLogger.detachAppender(appender);
        }
    }
}
