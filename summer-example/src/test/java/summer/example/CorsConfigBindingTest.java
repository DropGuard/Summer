package summer.example;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.test.annotation.SummerTest;
import summer.web.middleware.CorsConfig;

/**
 * Demonstrates {@code @SummerTest} with entry beans — no manual
 * {@code createContext()} needed.
 */
@SummerTest({CorsConfig.class})
class CorsConfigBindingTest {

    final BeanContainer context;

    CorsConfigBindingTest(BeanContainer context) {
        this.context = context;
    }

    @Test
    void bindsWithDefaults() {
        CorsConfig config = context.getBean(CorsConfig.class);
        assertNotNull(config);
        assertEquals(3600, config.maxAge());
    }
}