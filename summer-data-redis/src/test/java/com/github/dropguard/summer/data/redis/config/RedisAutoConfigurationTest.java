package com.github.dropguard.summer.data.redis.config;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.data.redis.SummerRedisTemplate;
import com.github.dropguard.summer.test.annotation.SummerTest;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Test;

@SummerTest
public class RedisAutoConfigurationTest {

    private final RedisProperties props;
    private final RedisClient client;
    private final SummerRedisTemplate template;

    public RedisAutoConfigurationTest(
            RedisProperties props, RedisClient client, SummerRedisTemplate template) {
        this.props = props;
        this.client = client;
        this.template = template;
    }

    @Test
    void contextLoadsAndCreatesRedisBeans() {
        assertNotNull(props);
        assertEquals("redis://localhost:6379", props.uri());
        assertNotNull(client);
        assertNotNull(template);
    }

    @Test
    void bindingIgnoresRawSystemProperty() {
        assertEquals(
                "redis://localhost:6379",
                props.uri(),
                "bound URI must come from @ConfigMapping default, not a raw system property");
    }
}
