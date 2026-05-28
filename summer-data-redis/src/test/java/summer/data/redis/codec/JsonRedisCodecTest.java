package summer.data.redis.codec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

public class JsonRedisCodecTest {

	public record TestUserRecord(String name, int age, LocalDateTime registeredAt) {
	}

	@Test
	public void testEncodeAndDecodeJavaRecordWithTime() {
		JsonRedisCodec codec = new JsonRedisCodec();

		TestUserRecord original = new TestUserRecord("Alice", 25, LocalDateTime.of(2023, 10, 1, 12, 30));

		ByteBuffer encodedBytes = codec.encodeValue(original);
		assertNotNull(encodedBytes);
		assertTrue(encodedBytes.remaining() > 0);

		Object decoded = codec.decodeValue(encodedBytes);

		assertNotNull(decoded);
		assertTrue(decoded instanceof TestUserRecord);
		TestUserRecord decodedUser = (TestUserRecord) decoded;

		assertEquals("Alice", decodedUser.name());
		assertEquals(25, decodedUser.age());
		assertEquals(LocalDateTime.of(2023, 10, 1, 12, 30), decodedUser.registeredAt());
	}
}
