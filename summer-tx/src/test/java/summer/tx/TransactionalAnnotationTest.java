package summer.tx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Transactional} annotation.
 */
class TransactionalAnnotationTest {

	@Test
	void shouldHaveTransactionalAnnotation() {
		Transactional annotation = getTransactionalAnnotation();
		assertNotNull(annotation);
	}

	@Test
	void shouldSupportTransactionalOnMethod() {
		assertTrue(isTransactionalMethodPresent());
	}

	@Test
	void shouldNotSupportTransactionalOnClass() {
		assertFalse(isTransactionalClassPresent());
	}

	private Transactional getTransactionalAnnotation() {
		try {
			Method method = TestService.class.getMethod("transactionalMethod");
			return method.getAnnotation(Transactional.class);
		} catch (NoSuchMethodException e) {
			fail("Method not found");
			return null;
		}
	}

	private boolean isTransactionalMethodPresent() {
		try {
			Method method = TestService.class.getMethod("transactionalMethod");
			return method.isAnnotationPresent(Transactional.class);
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	private boolean isTransactionalClassPresent() {
		return TestService.class.isAnnotationPresent(Transactional.class);
	}

	public interface TestService {
		@Transactional
		String transactionalMethod();

		String nonTransactionalMethod();
	}
}
