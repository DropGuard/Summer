package com.github.dropguard.summer.runtime.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.runtime.HandlerMetadata;
import com.github.dropguard.summer.web.ExceptionRegistry;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import com.github.dropguard.summer.web.Request;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RuntimeExceptionHandlerRegistrar}: valid metadata registers a working handler;
 * metadata drift (unloadable bean class, vanished handler method, unloadable exception type) fails
 * fast at startup instead of silently dropping the handler.
 */
class RuntimeExceptionHandlerRegistrarTest {

    /** Handler host whose methods are recorded in the metadata. */
    static class HandlerHost {
        boolean invoked;

        public void onError() {
            invoked = true;
        }
    }

    private static RuntimeExceptionHandlerRegistrar registrar(
            Map<String, List<BeanDefinition.ExceptionHandlerEntry>> entries) {
        return new RuntimeExceptionHandlerRegistrar(
                new HttpParameterResolverChain(List.of()), new HandlerMetadata(entries));
    }

    private static BeanDefinition.ExceptionHandlerEntry entry(String method, String exClass) {
        return new BeanDefinition.ExceptionHandlerEntry(method, exClass, 0);
    }

    @Test
    void registersHandlerForValidMetadata() throws Exception {
        HandlerHost host = new HandlerHost();
        BeanContainer.Builder builder = new BeanContainer.Builder();
        builder.register(HandlerHost.class, host);
        BeanContainer container = builder.build();
        ExceptionRegistry registry = new ExceptionRegistry();

        registrar(
                        Map.of(
                                HandlerHost.class.getName(),
                                List.of(
                                        entry(
                                                "onError",
                                                IllegalArgumentException.class.getName()))))
                .registerHandlers(registry, container);

        Handler handler = registry.getHandler(new IllegalArgumentException("x"));
        assertNotNull(handler, "a valid @ExceptionHandler entry must register a handler");
        handler.handle(new HttpContext(new Request(HttpMethod.GET, "/x", null, null, null)));
        assertTrue(host.invoked, "the handler must invoke the recorded method");
    }

    @Test
    void unknownBeanClassFailsFast() {
        ExceptionRegistry registry = new ExceptionRegistry();

        assertThrows(
                BeanCreationException.class,
                () ->
                        registrar(
                                        Map.of(
                                                "com.example.MissingHost",
                                                List.of(
                                                        entry(
                                                                "onError",
                                                                IllegalArgumentException.class
                                                                        .getName()))))
                                .registerHandlers(registry, container()));
    }

    @Test
    void missingHandlerMethodFailsFast() {
        ExceptionRegistry registry = new ExceptionRegistry();

        assertThrows(
                BeanCreationException.class,
                () ->
                        registrar(
                                        Map.of(
                                                HandlerHost.class.getName(),
                                                List.of(
                                                        entry(
                                                                "noSuchMethod",
                                                                IllegalArgumentException.class
                                                                        .getName()))))
                                .registerHandlers(registry, container()));
    }

    @Test
    void unloadableExceptionClassFailsFast() {
        ExceptionRegistry registry = new ExceptionRegistry();

        assertThrows(
                BeanCreationException.class,
                () ->
                        registrar(
                                        Map.of(
                                                HandlerHost.class.getName(),
                                                List.of(
                                                        entry(
                                                                "onError",
                                                                "com.example.NoSuchException"))))
                                .registerHandlers(registry, container()));
    }

    private static BeanContainer container() {
        BeanContainer.Builder builder = new BeanContainer.Builder();
        builder.register(HandlerHost.class, new HandlerHost());
        return builder.build();
    }
}
