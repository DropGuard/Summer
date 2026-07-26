package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for HTTP header extraction via Request.getHeader().
 */
public class HeaderExtractionTest {

	private Request requestWithHeaders(Map<String, String> headers) {
		byte[] pathBytes = "/api/test".getBytes(StandardCharsets.UTF_8);
		return new Request(HttpMethod.GET, "/api/test", "", "application/json", new byte[0], headers, pathBytes);
	}

	@Test
	void testGetStandardHeader() {
		Request request = requestWithHeaders(Map.of("content-type", "application/json", "accept", "application/json"));

		assertEquals("application/json", request.getHeader("content-type"));
		assertEquals("application/json", request.getHeader("accept"));
	}

	@Test
	void testHeaderLookupIsCaseInsensitive() {
		// getHeader() normalizes to lowercase
		Request request = requestWithHeaders(Map.of("authorization", "Bearer token123"));

		// Should work regardless of case used by the caller
		assertEquals("Bearer token123", request.getHeader("authorization"));
		assertEquals("Bearer token123", request.getHeader("Authorization"));
		assertEquals("Bearer token123", request.getHeader("AUTHORIZATION"));
	}

	@Test
	void testMissingHeaderReturnsNull() {
		Request request = requestWithHeaders(Map.of("content-type", "text/plain"));

		assertNull(request.getHeader("x-custom-header"));
	}

	@Test
	void testAuthorizationBearerHeader() {
		Request request = requestWithHeaders(Map.of("authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"));

		String auth = request.getHeader("Authorization");
		assertNotNull(auth);
		assertTrue(auth.startsWith("Bearer "));
		assertEquals("eyJhbGciOiJIUzI1NiJ9.payload.sig", auth.substring("Bearer ".length()));
	}

	@Test
	void testMultipleHeaders() {
		Request request = requestWithHeaders(Map.of("x-request-id", "req-abc-123", "x-forwarded-for", "192.168.1.1",
				"user-agent", "SummerTestClient/1.0"));

		assertEquals("req-abc-123", request.getHeader("x-request-id"));
		assertEquals("192.168.1.1", request.getHeader("x-forwarded-for"));
		assertEquals("SummerTestClient/1.0", request.getHeader("user-agent"));
	}
}
