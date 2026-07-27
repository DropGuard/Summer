package com.github.dropguard.summer.data.redis.config;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.data.redis.SummerRedisTemplate;
import com.github.dropguard.summer.test.Testing;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Test;

public class RedisAutoConfigurationTest {

    /**
     * Builds the full test universe through the framework's test channel (the same path
     * {@code @SummerTest} uses) and verifies the beans {@code RedisAutoConfiguration} actually
     * exposes after its lazy-connection refactor: the connection and sync-commands objects are no
     * longer beans — the {@link SummerRedisTemplate} opens the connection lazily on first use, so
     * the container assembles without a reachable Redis.
     */
    @Test
    public void testContextLoadsAndCreatesRedisBeans() {
        BeanContainer context = Testing.build();

        // The three beans the auto-configuration owns.
        RedisProperties props = context.getBean(RedisProperties.class);
        assertNotNull(props);
        assertEquals("redis://localhost:6379", props.uri());

        RedisClient client = context.getBean(RedisClient.class);
        assertNotNull(client);

        SummerRedisTemplate template = context.getBean(SummerRedisTemplate.class);
        assertNotNull(template);
    }
}
