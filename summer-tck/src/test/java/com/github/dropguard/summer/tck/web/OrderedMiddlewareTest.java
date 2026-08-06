package com.github.dropguard.summer.tck.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.Order;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.Middleware;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

@SummerTest
public class OrderedMiddlewareTest {

    // Declared OUT of @Order order on purpose: discovery order is [Second, First, Last], so this
    // test only passes because the resolved list is sorted by @Order — it would fail on the AOT
    // engine if the discovery-time slice were emitted as-is (the pre-fix divergence).
    @Component
    @Order(2)
    public static class SecondMw implements Middleware {
        public Handler apply(Handler next) {
            return next;
        }
    }

    @Component
    @Order(1)
    public static class FirstMw implements Middleware {
        public Handler apply(Handler next) {
            return next;
        }
    }

    @Component
    public static class LastMw implements Middleware {
        public Handler apply(Handler next) {
            return next;
        }
    }

    private final List<Middleware> middlewares;

    OrderedMiddlewareTest(List<Middleware> middlewares) {
        this.middlewares = middlewares;
    }

    private static final Set<Class<?>> TEST_MIDDLEWARES =
            Set.of(FirstMw.class, SecondMw.class, LastMw.class);

    @Test
    @DualEngine
    void orderAnnotationControlsSortOrder() {
        List<Class<?>> ordered =
                middlewares.stream()
                        .map(Object::getClass)
                        .filter(TEST_MIDDLEWARES::contains)
                        .toList();
        assertEquals(
                List.of(FirstMw.class, SecondMw.class, LastMw.class),
                ordered,
                "@Order(1) before @Order(2) before unannotated");
    }
}
