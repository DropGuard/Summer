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
        // Overrides are keyed by the dotted YAML path (ConfigBinder.BindingContext contract), not
        // the env-style name — the env-style key never matched the binding and the URI silently
        // fell back to the @WithDefault (localhost:6379). The ${COM_GITHUB_...} placeholder in the
        // YAML is the production 12-factor form; the test override binds redis.uri directly.
        return Map.of("redis.uri", "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
    }

    @Override
    public void stop() {
        if (redis != null) {
            redis.stop();
        }
    }
}
