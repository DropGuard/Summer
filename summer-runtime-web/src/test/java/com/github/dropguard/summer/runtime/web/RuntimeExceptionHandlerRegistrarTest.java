package com.github.dropguard.summer.runtime.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.runtime.InstantiatedBeans;
import com.github.dropguard.summer.web.ExceptionRegistry;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import com.github.dropguard.summer.web.Request;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RuntimeExceptionHandlerRegistrar}: valid metadata registers a working handler
 * (through the birth record, proxied or not); metadata drift (unloadable bean class, vanished
 * handler method, unloadable exception type, handler not exposed on the interface of an AOP-bound
 * bean) fails fast at startup instead of silently dropping handlers.
 */
class RuntimeExceptionHandlerRegistrarTest {

    /** Handler host whose methods are recorded in the metadata. */
    static class HandlerHost {
        boolean invoked;

        public void onError() {
            invoked = true;
        }
    }

    /** Public nested interface exposing the handler method — required for proxy dispatch. */
    public interface HandlerApi {
        void onError();
    }

    static class ProxiedHost implements HandlerApi {
        boolean invoked;

        @Override
        public void onError() {
            invoked = true;
        }

        // NOT on the interface — a bound bean must fail fast for this method.
        public void hiddenHandler() {}
    }

    /** Package-private interface — proxy dispatch from other packages must fail fast. */
    interface NonPublicApi {
        void onError();
    }

    static class NonPublicHost implements NonPublicApi {
        @Override
        public void onError() {}
    }

    private static RuntimeExceptionHandlerRegistrar registrar(
            Map<String, List<BeanDefinition.ExceptionHandlerEntry>> entries,
            Map<String, Object> instances) {
        InstantiatedBeans instantiated = new InstantiatedBeans(entries);
        instances.forEach(instantiated::record);
        return new RuntimeExceptionHandlerRegistrar(
                new HttpParameterResolverChain(List.of()), instantiated);
    }

    private static BeanDefinition.ExceptionHandlerEntry entry(String method, String exClass) {
        return new BeanDefinition.ExceptionHandlerEntry(method, exClass, 0);
    }

    private static HttpContext httpContext() {
        return new HttpContext(new Request(HttpMethod.GET, "/x", null, null, null));
    }

    private static BeanContainer container(Object host) {
        BeanContainer.Builder builder = new BeanContainer.Builder();
        builder.register(host.getClass(), host);
        return builder.build();
    }

    @Test
    void registersHandlerForValidMetadata() throws Exception {
        HandlerHost host = new HandlerHost();
        ExceptionRegistry registry = new ExceptionRegistry();

        registrar(
                        Map.of(
                                HandlerHost.class.getName(),
                                List.of(
                                        entry(
                                                "onError",
                                                IllegalArgumentException.class.getName()))),
                        Map.of(HandlerHost.class.getName(), host))
                .registerHandlers(registry, container(host));

        Handler handler = registry.getHandler(new IllegalArgumentException("x"));
        assertNotNull(handler, "a valid @ExceptionHandler entry must register a handler");
        handler.handle(httpContext());
        assertTrue(host.invoked, "the handler must invoke the recorded method");
    }

    @Test
    void proxyIncarnationDispatchesThroughInterfaceMethod() throws Exception {
        ProxiedHost raw = new ProxiedHost();
        Object proxy = registryingProxy(raw);
        ExceptionRegistry registry = new ExceptionRegistry();

        registrar(
                        Map.of(
                                ProxiedHost.class.getName(),
                                List.of(entry("onError", IllegalStateException.class.getName()))),
                        Map.of(ProxiedHost.class.getName(), proxy))
                .registerHandlers(registry, container(raw));

        Handler handler = registry.getHandler(new IllegalStateException("x"));
        assertNotNull(handler, "a proxy incarnation must register its interface handler");
        handler.handle(httpContext());
        assertTrue(raw.invoked, "the handler must dispatch through the proxy to the raw target");
    }

    @Test
    void proxyIncarnationWithoutInterfaceMethodFailsFast() {
        ProxiedHost raw = new ProxiedHost();
        Object proxy = registryingProxy(raw);
        ExceptionRegistry registry = new ExceptionRegistry();

        BeanCreationException thrown =
                assertThrows(
                        BeanCreationException.class,
                        () ->
                                registrar(
                                                Map.of(
                                                        ProxiedHost.class.getName(),
                                                        List.of(
                                                                entry(
                                                                        "hiddenHandler",
                                                                        IllegalStateException.class
                                                                                .getName()))),
                                                Map.of(ProxiedHost.class.getName(), proxy))
                                        .registerHandlers(registry, container(raw)));
        assertTrue(
                thrown.getMessage().contains("interface"),
                "the failure must name the interface contract: " + thrown.getMessage());
    }

    @Test
    void nonPublicDispatchInterfaceFailsFast() {
        ExceptionRegistry registry = new ExceptionRegistry();
        NonPublicHost raw = new NonPublicHost();
        Object proxy =
                Proxy.newProxyInstance(
                        NonPublicApi.class.getClassLoader(),
                        new Class<?>[] {NonPublicApi.class},
                        (p, m, a) -> {
                            m.invoke(raw, a);
                            return null;
                        });

        BeanCreationException thrown =
                assertThrows(
                        BeanCreationException.class,
                        () ->
                                registrar(
                                                Map.of(
                                                        NonPublicHost.class.getName(),
                                                        List.of(
                                                                entry(
                                                                        "onError",
                                                                        IllegalStateException.class
                                                                                .getName()))),
                                                Map.of(NonPublicHost.class.getName(), proxy))
                                        .registerHandlers(registry, container(raw)));
        assertTrue(
                thrown.getMessage().contains("not public"),
                "the failure must name the visibility requirement: " + thrown.getMessage());
    }

    @Test
    void neverInstantiatedBeanFailsFast() {
        ExceptionRegistry registry = new ExceptionRegistry();

        BeanCreationException thrown =
                assertThrows(
                        BeanCreationException.class,
                        () ->
                                registrar(
                                                Map.of(
                                                        HandlerHost.class.getName(),
                                                        List.of(
                                                                entry(
                                                                        "onError",
                                                                        IllegalArgumentException
                                                                                .class
                                                                                .getName()))),
                                                Map.of())
                                        .registerHandlers(registry, container(new HandlerHost())));
        assertTrue(
                thrown.getMessage().contains("never instantiated"),
                "the failure must describe the drift: " + thrown.getMessage());
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
                                                                        .getName()))),
                                        Map.of())
                                .registerHandlers(registry, container(new HandlerHost())));
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
                                                                        .getName()))),
                                        Map.of(HandlerHost.class.getName(), new HandlerHost()))
                                .registerHandlers(registry, container(new HandlerHost())));
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
                                                                "com.example.NoSuchException"))),
                                        Map.of(HandlerHost.class.getName(), new HandlerHost()))
                                .registerHandlers(registry, container(new HandlerHost())));
    }

    private static Object registryingProxy(ProxiedHost raw) {
        return Proxy.newProxyInstance(
                HandlerApi.class.getClassLoader(),
                new Class<?>[] {HandlerApi.class},
                (p, m, a) -> {
                    m.invoke(raw, a);
                    return null;
                });
    }
}
