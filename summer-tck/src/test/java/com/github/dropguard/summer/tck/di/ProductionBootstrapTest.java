package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.RuntimeDiMarker;
import com.github.dropguard.summer.engine.DiEngine;
import org.junit.jupiter.api.Test;

/**
 * Exercises the production reflective entry point ({@code DiEngine.create} → {@code
 * Class.forName(RuntimeBootstrap)} → static {@code build(Object...)}). The {@code @SummerTest} path
 * goes through the {@code ContainerEngine} SPI instead, so this is the only coverage of the
 * reflective bootstrap contract used by {@code SummerApplication} at production startup.
 */
class ProductionBootstrapTest {

    @Test
    void runtimeProductionEntryPointBuildsContainer() {
        BeanContainer container = DiEngine.create(Engine.RUNTIME);

        assertNotNull(container);
        // RuntimeDiMarker is registered by the runtime engine's pipeline — proof the
        // reflective entry point ran the real build (not a stub).
        assertTrue(container.containsBean(RuntimeDiMarker.class));
    }

    @Test
    void externalBeanResolvableByConcreteType() {
        ExternalGreeter greeter = new ExternalGreeter();

        BeanContainer container = DiEngine.create(Engine.RUNTIME, greeter);

        assertSame(greeter, container.getBean(ExternalGreeter.class));
    }

    @Test
    void externalBeanResolvableByInterface() {
        // External beans are deliberately registered under their concrete class only
        // (RuntimeContainer
        // registers no interface keys for them). Interface injection still works:
        // BeanContainer.getBean
        // resolves by assignability (isInstance scan), so a single-impl interface finds the
        // registered
        // external bean without an interface key.
        ExternalGreeter greeter = new ExternalGreeter();

        BeanContainer container = DiEngine.create(Engine.RUNTIME, greeter);

        assertSame(greeter, container.getBean(GreetingService.class));
    }

    /**
     * External fixture: an interface plus a single implementation passed in as an external bean.
     */
    public interface GreetingService {
        String greet();
    }

    public static class ExternalGreeter implements GreetingService {
        @Override
        public String greet() {
            return "hi";
        }
    }
}
