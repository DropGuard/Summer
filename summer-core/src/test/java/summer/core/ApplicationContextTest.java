package summer.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import summer.core.exception.BeansException;

/**
 * Tests for {@link ApplicationContext} interface contract.
 */
class ApplicationContextTest {

	@Test
	void shouldCreateAnonymousImplementation() {
		ApplicationContext context = new ApplicationContext() {
			@Override
			public <T> T getBean(Class<T> type) {
				return null;
			}

			@Override
			public <T> List<T> getBeansOfType(Class<T> type) {
				return List.of();
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

		assertNotNull(context);
		assertNull(context.getBean(String.class));
		assertTrue(context.getBeansOfType(String.class).isEmpty());
		assertTrue(context.getComponentClasses().isEmpty());
	}

	@Test
	void shouldThrowWhenAotContextNotFound() {
		assertThrows(BeansException.class, ApplicationContext::aot);
	}
}