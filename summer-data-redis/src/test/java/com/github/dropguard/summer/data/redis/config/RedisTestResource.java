package com.github.dropguard.summer.data.redis.config;

import com.github.dropguard.summer.test.TestResource;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class RedisTestResource implements TestResource {

    private GenericContainer<?> redis;

    @Override
    public Map<String, String> start() {
        redis =
                new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379);
        redis.start();
        return Map.of(
                "COM_GITHUB_DROPGUARD_SUMMER_REDIS_URI",
                "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
    }

    @Override
    public void stop() {
        if (redis != null) {
            redis.stop();
        }
    }
}
