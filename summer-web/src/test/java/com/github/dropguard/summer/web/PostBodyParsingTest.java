package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Tests for POST body parsing via WebContext.body(RecordType.class). Summer supports both Records
 * and Maps for request DTOs.
 */
public class PostBodyParsingTest {

    // ---- DTO definitions (must be Records) ----

    record CreateUserRequest(String name, String email, int age) {}

    record LoginRequest(String username, String password) {}

    // ---- Helpers ----

    private HttpContext jsonPostContext(String jsonBody) {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        Request request = new Request(HttpMethod.POST, "/", "", "application/json", bodyBytes);
        return new HttpContext(request);
    }

    // ---- Tests ----

    @Test
    void testSimpleJsonBodyDeserialization() {
        String json =
                """
                {"name": "Alice", "email": "alice@example.com", "age": 30}
                """;
        HttpContext ctx = jsonPostContext(json);

        CreateUserRequest dto = ctx.body(CreateUserRequest.class);

        assertNotNull(dto);
        assertEquals("Alice", dto.name());
        assertEquals("alice@example.com", dto.email());
        assertEquals(30, dto.age());
    }

    @Test
    void testJsonBodyWithMissingFieldDefaultsToNull() {
        // age is missing --Jackson should default it to 0 for primitives
        String json =
                """
                {"name": "Bob", "email": "bob@example.com"}
                """;
        HttpContext ctx = jsonPostContext(json);

        CreateUserRequest dto = ctx.body(CreateUserRequest.class);
        assertNotNull(dto);
        assertEquals("Bob", dto.name());
        assertEquals(0, dto.age()); // primitive int defaults to 0
    }

    @Test
    void testJsonBodyWithExtraFieldsIsIgnored() {
        // Jackson is configured with FAIL_ON_UNKNOWN_PROPERTIES=false
        String json =
                """
                {"username": "charlie", "password": "s3cr3t", "unexpectedField": "should be ignored"}
                """;
        HttpContext ctx = jsonPostContext(json);

        LoginRequest dto = ctx.body(LoginRequest.class);
        assertNotNull(dto);
        assertEquals("charlie", dto.username());
        assertEquals("s3cr3t", dto.password());
    }

    @Test
    void testEmptyBodyThrowsOnParse() {
        HttpContext ctx = jsonPostContext("");

        assertThrows(RuntimeException.class, () -> ctx.body(CreateUserRequest.class));
    }
}
