package summer.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import summer.core.BeanContainer;
import summer.data.redis.SummerRedisTemplate;
import summer.test.annotation.DualEngine;
import summer.test.annotation.SummerTest;

/**
 * Framework integration test on a REAL Postgres + Redis stack.
 *
 * <p>
 * Verifies the framework contract that demos rely on, but which must NOT be
 * validated by a demo: a {@code @SummerTest} universe builds over a real
 * database, {@code @Mock}/{@code @TestProfile} behave identically on both DI
 * engines (dual-engine parity), and Redis is wired against a live server. This
 * is the framework asserting its own correctness — the Twitter demo no longer
 * carries this burden.
 * </p>
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
}
