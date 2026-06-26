package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExceptionRegistry}.
 */
class ExceptionRegistryTest {

	@Test
	void shouldReturnHandlerForExactMatch() {
		ExceptionRegistry registry = new ExceptionRegistry();
		AtomicReference<String> called = new AtomicReference<>();
		Handler handler = ctx -> {
			called.set("handled");
		};

		registry.register(RuntimeException.class, handler);

		Handler found = registry.getHandler(new RuntimeException("test"));
		assertSame(handler, found);
	}

	@Test
	void shouldWalkUpInheritanceChain() {
		ExceptionRegistry registry = new ExceptionRegistry();
		AtomicReference<String> called = new AtomicReference<>();
		Handler handler = ctx -> {
			called.set("handled");
		};

		registry.register(RuntimeException.class, handler);

		// IllegalArgumentException extends RuntimeException
		Handler found = registry.getHandler(new IllegalArgumentException("bad arg"));
		assertSame(handler, found);
	}

	@Test
	void shouldReturnNullWhenNoMatch() {
		ExceptionRegistry registry = new ExceptionRegistry();

		Handler found = registry.getHandler(new RuntimeException("test"));
		assertNull(found);
	}

	@Test
	void shouldMatchMostSpecificType() {
		ExceptionRegistry registry = new ExceptionRegistry();
		Handler generalHandler = ctx -> {
		};
		Handler specificHandler = ctx -> {
		};

		registry.register(Exception.class, generalHandler);
		registry.register(IllegalArgumentException.class, specificHandler);

		assertSame(specificHandler, registry.getHandler(new IllegalArgumentException("test")));
		assertSame(generalHandler, registry.getHandler(new RuntimeException("test")));
	}

	@Test
	void shouldHandleDeepInheritance() {
		ExceptionRegistry registry = new ExceptionRegistry();
		Handler handler = ctx -> {
		};

		registry.register(Throwable.class, handler);

		// NullPointerException -> RuntimeException -> Throwable
		assertSame(handler, registry.getHandler(new NullPointerException()));
	}
}
