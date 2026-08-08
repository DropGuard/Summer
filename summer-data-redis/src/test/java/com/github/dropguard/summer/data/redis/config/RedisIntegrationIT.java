package com.github.dropguard.summer.data.redis.config;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.data.redis.SummerRedisTemplate;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import java.time.LocalDateTime;

@SummerTest
@TestResource(RedisTestResource.class)
public class RedisIntegrationIT {

    private final SummerRedisTemplate template;

    public RedisIntegrationIT(SummerRedisTemplate template) {
        this.template = template;
    }

    record TestUserRecord(String name, int age, LocalDateTime registeredAt) {}

    @DualEngine
    void realRedisOperations() {
        assertNotNull(template);

        String key = "test:user:1";
        TestUserRecord user = new TestUserRecord("Bob", 30, LocalDateTime.of(2023, 11, 20, 15, 0));

        template.set(key, user);

        TestUserRecord retrieved = template.get(key, TestUserRecord.class);
        assertNotNull(retrieved);
        assertEquals("Bob", retrieved.name());
        assertEquals(30, retrieved.age());
        assertEquals(LocalDateTime.of(2023, 11, 20, 15, 0), retrieved.registeredAt());
    }
}
