package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;
import summer.core.AotDiMarker;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.fixtures.di.configprops.AppProperties;
import summer.test.annotation.SummerTest;
import summer.web.ExceptionHandlerRegistrar;

@SummerTest(engine = Engine.AOT, value = {AppProperties.class})
class AotLocalContextTest {

	final BeanContainer context;

	AotLocalContextTest(BeanContainer context) {
		this.context = context;
	}

	@Test
	void shouldLoadLocalContextWithOnlyEntryBean() {
		assertNotNull(context);
		assertEquals(Engine.AOT, context.engine());

		AppProperties props = context.getBean(AppProperties.class);
		assertNotNull(props);
		assertEquals("summer-tck", props.name());
		assertEquals(8080, props.port());
		assertTrue(props.verbose());

		// LocalContext should contain exactly: AotDiMarker, AppProperties,
		// ExceptionHandlerRegistrar
		assertEquals(Set.of(AotDiMarker.class, AppProperties.class, ExceptionHandlerRegistrar.class),
				Set.copyOf(context.componentTypes()), "LocalContext should contain only framework infra + entry bean");
	}
}
