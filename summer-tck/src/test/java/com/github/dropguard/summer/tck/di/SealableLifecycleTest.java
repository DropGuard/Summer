package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.tck.invisible.fixtures.lifecycle.ContainerTimedSealable;
import com.github.dropguard.summer.tck.invisible.fixtures.lifecycle.SelfFreezingBean;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The seal contract: {@code Sealable.seal()} is called by the container exactly once per bean, at
 * the end of assembly (after every bean is constructed, before {@code build()} returns), on both
 * engines; self-freezing beans (constructor-end {@code FrozenState}) work without any container
 * involvement; and post-seal writes fail.
 */
class SealableLifecycleTest {

    @ParameterizedTest
    @EnumSource(Engine.class)
    void containerSealsSealableBeansAtEndOfAssembly(Engine engine) {
        // Static fixture state persists across engine invocations in one JVM — reset per leg.
        ContainerTimedSealable.SEALED.set(false);
        ContainerTimedSealable.SEAL_COUNT.set(0);
        BeanContainer container =
                TestContainer.builder()
                        .testClass(getClass())
                        .engine(engine)
                        .beans(ContainerTimedSealable.class)
                        .build();

        // Seal ran during build — by the time build() returns, the bean is sealed.
        assertTrue(ContainerTimedSealable.SEALED.get(), "seal must run before build() returns");
        assertEquals(
                1,
                ContainerTimedSealable.SEAL_COUNT.get(),
                "exactly one seal per bean — the container holds one incarnation");

        // Post-seal writes fail on the bean's own guard.
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> container.getBean(ContainerTimedSealable.class).mutate());
        assertTrue(e.getMessage().contains("frozen"), e.getMessage());
    }

    @Test
    void selfFreezingBeanNeedsNoContainerTiming() {
        BeanContainer container =
                TestContainer.builder().testClass(getClass()).beans(SelfFreezingBean.class).build();

        // Frozen on the constructor's last line: every write fails from the moment the bean is
        // served, with no container involvement. seal() afterwards is an idempotent no-op.
        SelfFreezingBean bean = container.getBean(SelfFreezingBean.class);
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> bean.append("any"));
        assertTrue(e.getMessage().contains("frozen"), e.getMessage());
        bean.seal();
        assertThrows(IllegalStateException.class, () -> bean.append("still"));
        assertTrue(SelfFreezingBean.WRITTEN.isEmpty(), "no write may ever land after freezing");
    }
}
