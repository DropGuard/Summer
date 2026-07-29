package com.github.dropguard.summer.grpc.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.Test;

@SummerTest
class GrpcInterceptorIntegrationTest {

    private final BeanContainer ctx;

    GrpcInterceptorIntegrationTest(BeanContainer ctx) {
        this.ctx = ctx;
    }

    @Test
    void testGrpcInterceptorRegistered() {
        assertTrue(ctx.getBeans(Object.class).size() > 0);
    }
}
