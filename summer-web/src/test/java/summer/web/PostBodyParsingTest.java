package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Tests for POST body parsing via WebContext.body(RecordType.class). Note:
 * Summer enforces immutable Records for all request DTOs.
 */
public class PostBodyParsingTest {

	// ---- DTO definitions (must be Records) ----

	record CreateUserRequest(String name, String email, int age) {
	}

	record LoginRequest(String username, String password) {
	}

	// ---- Helpers ----

	private WebContext jsonPostContext(String jsonBody) {
		byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
		Request request = new Request("POST", "/", "", "application/json", bodyBytes);
		return new WebContext(request, null);
	}

	// ---- Tests ----

	@Test
	void testSimpleJsonBodyDeserialization() {
		String json = """
				{"name": "Alice", "email": "alice@example.com", "age": 30}
				""";
		WebContext ctx = jsonPostContext(json);

		CreateUserRequest dto = ctx.body(CreateUserRequest.class);

		assertNotNull(dto);
		assertEquals("Alice", dto.name());
		assertEquals("alice@example.com", dto.email());
		assertEquals(30, dto.age());
	}

	@Test
	void testJsonBodyWithMissingFieldDefaultsToNull() {
		// age is missing — Jackson should default it to 0 for primitives
		String json = """
				{"name": "Bob", "email": "bob@example.com"}
				""";
		WebContext ctx = jsonPostContext(json);

		CreateUserRequest dto = ctx.body(CreateUserRequest.class);
		assertNotNull(dto);
		assertEquals("Bob", dto.name());
		assertEquals(0, dto.age()); // primitive int defaults to 0
	}

	@Test
	void testJsonBodyWithExtraFieldsIsIgnored() {
		// Jackson is configured with FAIL_ON_UNKNOWN_PROPERTIES=false
		String json = """
				{"username": "charlie", "password": "s3cr3t", "unexpectedField": "should be ignored"}
				""";
		WebContext ctx = jsonPostContext(json);

		LoginRequest dto = ctx.body(LoginRequest.class);
		assertNotNull(dto);
		assertEquals("charlie", dto.username());
		assertEquals("s3cr3t", dto.password());
	}

	@Test
	void testBodyRequiresRecord() {
		// Non-record class should be rejected by Summer's architecture constraint
		class NotARecord {
			String name;
		}

		String json = """
				{"name": "Dave"}
				""";
		WebContext ctx = jsonPostContext(json);

		assertThrows(summer.core.SummerException.class, () -> ctx.body(NotARecord.class));
	}

	@Test
	void testEmptyBodyThrowsOnParse() {
		WebContext ctx = jsonPostContext("");

		assertThrows(RuntimeException.class, () -> ctx.body(CreateUserRequest.class));
	}
}
