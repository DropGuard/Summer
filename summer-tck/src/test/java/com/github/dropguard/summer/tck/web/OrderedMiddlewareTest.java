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

    @Component
    @Order(1)
    public static class FirstMw implements Middleware {
        public Handler apply(Handler next) {
            return next;
        }
    }

    @Component
    @Order(2)
    public static class SecondMw implements Middleware {
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
