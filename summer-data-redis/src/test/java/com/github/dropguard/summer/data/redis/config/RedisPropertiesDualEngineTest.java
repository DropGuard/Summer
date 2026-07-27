package com.github.dropguard.summer.data.redis.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Verifies the Quarkus-style {@code @ConfigMapping} RedisProperties binds identically under both
 * the Runtime (proxy) and AOT (generated {@code $$ConfigImpl}) engines — proving the whole chain on
 * a real bean consumed by {@code RedisAutoConfiguration}.
 */
@SummerTest
public class RedisPropertiesDualEngineTest {

    private final BeanContainer context;

    public RedisPropertiesDualEngineTest(BeanContainer context) {
        this.context = context;
    }

    @DualEngine
    void bindsRedisUriFromDefault() {
        RedisProperties props = context.getBean(RedisProperties.class);
        assertNotNull(props);
        assertEquals("redis://localhost:6379", props.uri());
    }
}
