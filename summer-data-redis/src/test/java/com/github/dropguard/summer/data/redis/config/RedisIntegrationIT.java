package com.github.dropguard.summer.data.redis.config;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.data.redis.SummerRedisTemplate;
import com.github.dropguard.summer.test.Testing;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class RedisIntegrationIT {

    @Container
    public static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    public record TestUserRecord(String name, int age, LocalDateTime registeredAt) {}

    @Test
    public void testRedisAutoConfigurationWithRealContainer() {
        // Override the bound URI through the framework's ${VAR} placeholder
        // convention (env var / system property), matching how datasource URIs are
        // externalized. The production auto-configuration reads only the bound
        // @ConfigMapping value, never a raw system property.
        String redisUri = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();
        System.setProperty("COM_GITHUB_DROPGUARD_SUMMER_REDIS_URI", redisUri);

        // The connection is opened lazily by the template, so the container builds
        // without a live Redis; the real operations below exercise the connection.
        BeanContainer context = Testing.build();

        try {
            SummerRedisTemplate template = context.getBean(SummerRedisTemplate.class);
            assertNotNull(template);

            // Perform real network operations through the template
            String key = "test:user:1";
            TestUserRecord user =
                    new TestUserRecord("Bob", 30, LocalDateTime.of(2023, 11, 20, 15, 0));

            template.set(key, user);

            TestUserRecord retrievedUser = template.get(key, TestUserRecord.class);
            assertNotNull(retrievedUser);
            assertEquals("Bob", retrievedUser.name());
            assertEquals(30, retrievedUser.age());
            assertEquals(LocalDateTime.of(2023, 11, 20, 15, 0), retrievedUser.registeredAt());
        } finally {
            System.clearProperty("COM_GITHUB_DROPGUARD_SUMMER_REDIS_URI");
            try {
                context.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
