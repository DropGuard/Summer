package summer.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Provider} interface.
 */
class ProviderTest {

	@Test
	void shouldCreateProvider() {
		Provider<String> provider = () -> "Hello, World!";
		assertEquals("Hello, World!", provider.provide());
	}

	@Test
	void shouldCreateProviderWithNull() {
		Provider<Object> provider = () -> null;
		assertNull(provider.provide());
	}

	@Test
	void shouldCreateProviderWithComplexObject() {
		Provider<TestService> provider = TestService::new;
		TestService service = provider.provide();
		assertNotNull(service);
		assertEquals("TestService", service.getName());
	}

	@Test
	void shouldSupportFunctionalInterface() {
		Provider<Integer> provider = () -> 42;
		assertEquals(42, provider.provide());
	}

	@Test
	void shouldSupportLambdaExpressions() {
		Provider<String> provider = () -> {
			String result = "Computed";
			return result;
		};
		assertEquals("Computed", provider.provide());
	}

	// Test helper class
	public static class TestService {
		public String getName() {
			return "TestService";
		}
	}
}