package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RouterRegistry}. */
class RouterRegistryTest {

    @Test
    void shouldRegisterAndRetrieveHttpFactory() {
        RouterRegistry registry = new RouterRegistry();
        Function<List<HttpRouter.Builder.Route>, HttpRouter> factory = routes -> null;

        registry.registerHttp(RouterType.RADIX_TREE, factory);

        assertSame(factory, registry.httpFactory(RouterType.RADIX_TREE));
    }

    @Test
    void shouldRegisterAndRetrieveWsFactory() {
        RouterRegistry registry = new RouterRegistry();
        Function<List<WsRouter.WsRoute>, WsRouter> factory = routes -> null;

        registry.registerWs(RouterType.RADIX_TREE, factory);

        assertSame(factory, registry.wsFactory(RouterType.RADIX_TREE));
    }

    @Test
    void shouldThrowWhenHttpFactoryNotRegistered() {
        RouterRegistry registry = new RouterRegistry();

        assertThrows(
                IllegalArgumentException.class, () -> registry.httpFactory(RouterType.RADIX_TREE));
    }

    @Test
    void shouldThrowWhenWsFactoryNotRegistered() {
        RouterRegistry registry = new RouterRegistry();

        assertThrows(
                IllegalArgumentException.class, () -> registry.wsFactory(RouterType.RADIX_TREE));
    }

    @Test
    void shouldOverwriteHttpFactory() {
        RouterRegistry registry = new RouterRegistry();
        Function<List<HttpRouter.Builder.Route>, HttpRouter> first = routes -> null;
        Function<List<HttpRouter.Builder.Route>, HttpRouter> second = routes -> null;

        registry.registerHttp(RouterType.RADIX_TREE, first);
        registry.registerHttp(RouterType.RADIX_TREE, second);

        assertSame(second, registry.httpFactory(RouterType.RADIX_TREE));
    }
}
