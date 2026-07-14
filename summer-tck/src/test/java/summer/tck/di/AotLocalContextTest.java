package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.core.AotDiMarker;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.fixtures.di.configprops.AppProperties;
import summer.test.TestContainerBuilder;
import summer.tck.annotation.WithFixtures;
import summer.web.ExceptionHandlerRegistrar;

/**
 * Verifies that the AOT LocalContext mechanism produces a correctly-scoped
 * container.
 *
 * <p>
 * Declares its seeds via {@code @WithFixtures} and loads them via
 * {@link summer.test.TestContainerBuilder#buildAot(Class)}. The SummerMojo
 * generates a {@code LocalContext_AotLocalContextTest} at build time containing
 * only the transitive closure of those seeds.
 * </p>
 */
@WithFixtures({AppProperties.class})
class AotLocalContextTest {

	private BeanContainer context;

	@BeforeEach
	void setUp() {
		context = TestContainerBuilder.buildAot(getClass());
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			try {
				context.close();
			} catch (Exception ignored) {
			}
		}
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
