package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;
import summer.core.Component;
import summer.core.exception.BeanCreationException;

class RuntimeComponentScannerTest {

	@Test
	void shouldRejectComponentOnInterface() throws Exception {
		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> RuntimeComponentScanner.transitiveExpand(Set.of(InvalidInterfaceComponent.class), Index.of(Object.class)));
		assertTrue(ex.getMessage().contains("cannot be placed on an interface or abstract class"));
	}

	@Test
	void shouldRejectComponentOnAbstractClass() throws Exception {
		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> RuntimeComponentScanner.transitiveExpand(Set.of(InvalidAbstractComponent.class), Index.of(Object.class)));
		assertTrue(ex.getMessage().contains("cannot be placed on an interface or abstract class"));
	}

	@Test
	void shouldRejectClassWithoutComponentAnnotation() throws Exception {
		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> RuntimeComponentScanner.transitiveExpand(Set.of(MissingAnnotationClass.class), Index.of(Object.class)));
		assertTrue(ex.getMessage().contains("is not annotated with @Component or @ConfigurationProperties"));
	}

	@Component
	interface InvalidInterfaceComponent {
	}

	@Component
	abstract class InvalidAbstractComponent {
	}

	static class MissingAnnotationClass {
	}
}
