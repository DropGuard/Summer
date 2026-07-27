package com.github.dropguard.summer.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.data.redis.SummerRedisTemplate;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.internal.SummerTestLifecycle;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterAll;

/**
 * Framework integration test on a REAL Postgres + Redis stack.
 *
 * <p>Verifies the framework contract that demos rely on, but which must NOT be validated by a demo:
 * a {@code @SummerTest} universe builds over a real database, {@code @Mock}/{@code @TestProfile}
 * behave identically on both DI engines (dual-engine parity), and Redis is wired against a live
 * server. This is the framework asserting its own correctness — the Twitter demo no longer carries
 * this burden.
 */
@SummerTest
public class GreetingIT extends AbstractFrameworkIT {

    @DualEngine
    void realPostgresRoundTrip() {
        GreetingRepository repo = context.getBean(GreetingRepository.class);
        // Idempotent: both engines (Runtime + AOT) reuse the same shared Postgres,
        // so delete-then-insert avoids a duplicate-key clash on the second run.
        repo.delete(1L);
        repo.insert(new Greeting(1L, "hello from real pg"));
        Greeting found = repo.findById(1L);
        assertNotNull(found);
        assertEquals("hello from real pg", found.text());
        assertEquals(1, repo.all().size());
    }

    @DualEngine
    void realRedisRoundTrip() {
        SummerRedisTemplate template = context.getBean(SummerRedisTemplate.class);
        assertNotNull(template);
        String key = "it:user:1";
        template.set(key, new ItUser("Carol", 41, LocalDateTime.of(2024, 1, 2, 3, 4)));
        ItUser got = template.get(key, ItUser.class);
        assertNotNull(got);
        assertEquals("Carol", got.name());
        assertEquals(41, got.age());
    }

    @DualEngine
    void universeContainsFrameworkBeans() {
        // Both engines must discover the same real-JDBC component.
        BeanContainer c = context;
        assertTrue(c.getBeans(GreetingRepository.class).iterator().hasNext());
    }

    /**
     * Pins the universe-reuse mechanism: this class has 3 {@code @DualEngine} methods, each run on
     * both engines, so {@code acquireUniverse} is entered 6 times. All 6 share one EnvKey (same
     * class, no profile, no mocks), so the first build is a miss and the remaining 5 are cache
     * hits. If reuse ever regresses (e.g. the cache is bypassed or the EnvKey stops distinguishing
     * per-class), this assertion fails — no opt-in switch required.
     *
     * <p>Adding/removing {@code @DualEngine} methods here must update this expected count (methods
     * × engines − 1).
     */
    @AfterAll
    static void universeReuseIsExercised() {
        assertEquals(
                5,
                SummerTestLifecycle.instance().cacheHits(),
                "expected 3 @DualEngine methods × 2 engines − 1 first build = 5 cache hits");
    }
}
