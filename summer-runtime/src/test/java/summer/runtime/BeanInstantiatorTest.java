package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.core.bean.BeanDefinition;
import summer.core.exception.BeanCreationException;

class BeanInstantiatorTest {

	@Test
	void shouldWrapConstructorException() {
		BeanContainer.Builder builder = new BeanContainer.Builder();
		BeanInstantiator instantiator = new BeanInstantiator(builder, Map.of(), Map.of());

		BeanDefinition def = new BeanDefinition(CrashingComponent.class.getName(), "crashingComponent");

		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> instantiator.instantiateFromDefinition(def));

		assertTrue(ex.getMessage().contains("Failed to instantiate bean"));
		assertNotNull(ex.getCause());
		assertEquals("Crash", ex.getCause().getCause().getMessage());
	}

	@Test
	void shouldRejectBeanContainerInjection() {
		BeanContainer.Builder builder = new BeanContainer.Builder();
		BeanInstantiator instantiator = new BeanInstantiator(builder, Map.of(), Map.of());

		BeanDefinition def = new BeanDefinition(ContainerInjectingComponent.class.getName(),
				"containerInjectingComponent");
		def.constructorParamTypes.add(BeanContainer.class.getName());

		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> instantiator.instantiateFromDefinition(def));

		assertTrue(ex.getCause().getMessage().contains("ApplicationContext injection is not supported"));
	}

	@Test
	void shouldWrapClassNotFoundException() {
		BeanContainer.Builder builder = new BeanContainer.Builder();
		BeanInstantiator instantiator = new BeanInstantiator(builder, Map.of(), Map.of());

		BeanDefinition def = new BeanDefinition("com.example.NonExistentClass", "missing");

		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> instantiator.instantiateFromDefinition(def));

		assertTrue(ex.getMessage().contains("Class not found: com.example.NonExistentClass"));
		assertTrue(ex.getCause() instanceof ClassNotFoundException);
	}

	public static class CrashingComponent {
		public CrashingComponent() {
			throw new RuntimeException("Crash");
		}
	}

	public static class ContainerInjectingComponent {
		public ContainerInjectingComponent(BeanContainer container) {
		}
	}
}
