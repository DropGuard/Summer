package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;
import summer.core.Component;
import summer.core.exception.UnsupportedInjectionException;

class RuntimeBeanAdapterTest {

	@Test
	void shouldRejectNestedGenericListInjection() throws Exception {
		UnsupportedInjectionException ex = assertThrows(UnsupportedInjectionException.class, 
			() -> new RuntimeBeanAdapter(Index.of(Object.class)).adaptComponent(NestedGenericComponent.class));
			
		assertTrue(ex.getMessage().contains("Nested generic type injection is not supported"));
		assertTrue(ex.getMessage().contains("List<"));
	}

	@Component
	public static class NestedGenericComponent {
		public NestedGenericComponent(List<Strategy<String>> strategies) {
		}
	}

	public interface Strategy<T> {
	}
}
