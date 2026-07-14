package summer.twitter.infra;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Pure unit test — no DI container needed.
 */
class SnowflakeIdGeneratorTest {

	private final SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

	@Test
	void generatesUniqueIds() {
		long id1 = generator.nextId();
		long id2 = generator.nextId();
		long id3 = generator.nextId();

		assertTrue(id1 > 0);
		assertTrue(id2 > id1, "IDs should be monotonically increasing");
		assertTrue(id3 > id2, "IDs should be monotonically increasing");
		assertNotEquals(id1, id2, "IDs must be unique");
		assertNotEquals(id2, id3, "IDs must be unique");
	}

	@Test
	void generatesManyUniqueIds() {
		long prev = 0;
		for (int i = 0; i < 10_000; i++) {
			long id = generator.nextId();
			assertTrue(id > prev, "ID at index " + i + " should be greater than previous");
			prev = id;
		}
	}
}
