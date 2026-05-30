package summer.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;
import summer.core.exception.BeansException;

/**
 * Tests for {@link ApplicationContext} interface contract.
 */
class ApplicationContextTest {

	@Test
	void shouldThrowWhenContextNotInitialized() {
		// Reset the global context
		assertThrows(BeansException.class, ApplicationContext::getInstance);
	}

	@Test
	void shouldSetAndGetGlobalContext() {
		// Create a mock context
		ApplicationContext mockContext = new ApplicationContext() {
			@Override
			public <T> T getBean(Class<T> type) {
				return null;
			}

			@Override
			public <T> java.util.List<T> getBeansOfType(Class<T> type) {
				return java.util.List.of();
			}

			@Override
			public Set<Class<?>> getComponentClasses() {
				return Set.of();
			}

			@Override
			public void destroy() {
				// no-op
			}
		};

		// Initialize the global context
		ApplicationContext.init(mockContext);
		assertSame(mockContext, ApplicationContext.getInstance());

		// Cleanup
		ApplicationContext.init(null);
	}

	@Test
	void shouldHandleNullInitialization() {
		// Reset the global context
		ApplicationContext.init(null);
		assertThrows(BeansException.class, ApplicationContext::getInstance);
	}
}