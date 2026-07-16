package summer.example;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import summer.test.annotation.SummerTest;
import summer.web.middleware.CorsConfig;

/**
 * Integration test — requires Redis on localhost:6379.
 * Demonstrates {@code @SummerTest} with constructor injection.
 */
@SummerTest
@Tag("integration")
class CorsConfigBindingTest {

    final CorsConfig config;

    CorsConfigBindingTest(CorsConfig config) {
        this.config = config;
    }

    @Test
    void bindsWithDefaults() {
        assertNotNull(config);
        assertEquals(3600, config.maxAge());
    }
}
