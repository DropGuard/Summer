package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;
import summer.core.Component;
import summer.core.bean.BeanClosure;
import summer.core.exception.BeanCreationException;

/**
 * Tests for {@link BeanClosure} seed validation and closure computation.
 */
class BeanClosureTest {

	@Test
	void shouldRejectComponentOnInterface() {
		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> BeanClosure.validateSeeds(
						Set.of(InvalidInterfaceComponent.class.getName()),
						Index.of(InvalidInterfaceComponent.class)));
		assertTrue(ex.getMessage().contains("cannot be placed on an interface or abstract class"));
	}

	@Test
	void shouldRejectComponentOnAbstractClass() {
		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> BeanClosure.validateSeeds(
						Set.of(InvalidAbstractComponent.class.getName()),
						Index.of(InvalidAbstractComponent.class)));
		assertTrue(ex.getMessage().contains("cannot be placed on an interface or abstract class"));
	}

	@Test
	void shouldRejectClassWithoutComponentAnnotation() {
		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> BeanClosure.validateSeeds(
						Set.of(MissingAnnotationClass.class.getName()),
						Index.of(MissingAnnotationClass.class)));
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
