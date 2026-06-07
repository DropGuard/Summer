package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.AbstractContextTCK;
import summer.tck.di.configprops.*;

/**
 * TCK contract for {@code @ConfigurationProperties} auto-binding.
 *
 * <p>
 * Verifies that the DI engine:
 * <ul>
 * <li>Discovers {@code @ConfigurationProperties} records via Jandex</li>
 * <li>Binds YAML values to record components</li>
 * <li>Leaves unconfigured fields as {@code null}</li>
 * <li>Registers the bound record as a singleton bean</li>
 * <li>Makes it injectable into {@code @Component} and {@code @Bean}
 * dependencies</li>
 * </ul>
 */
public abstract class AbstractConfigurationPropertiesTCK extends AbstractContextTCK {

	@Test
	void testPropertiesBeanRegistered() {
		ApplicationContext ctx = context();
		AppProperties props = ctx.getBean(AppProperties.class);
		assertNotNull(props, "@ConfigurationProperties record should be registered as a bean");
	}

	@Test
	void testYamlValuesBound() {
		AppProperties props = context().getBean(AppProperties.class);
		assertEquals("summer-tck", props.name(), "String value from YAML should be bound");
		assertEquals(Integer.valueOf(8080), props.port(), "Integer value from YAML should be bound");
	}

	@Test
	void testUnconfiguredFieldIsNull() {
		AppProperties props = context().getBean(AppProperties.class);
		assertNull(props.verbose(), "Field absent from YAML should be null");
	}

	@Test
	void testInjectableIntoBeanMethod() {
		ApplicationContext ctx = context();
		AppService service = ctx.getBean(AppService.class);
		assertNotNull(service, "@Bean that depends on config properties should be created");
		assertSame(ctx.getBean(AppProperties.class), service.getProperties(),
				"@Bean should receive the same config properties instance");
	}

	@Test
	void testInjectableIntoComponent() {
		ApplicationContext ctx = context();
		PropertiesConsumer consumer = ctx.getBean(PropertiesConsumer.class);
		assertNotNull(consumer, "@Component that depends on config properties should be created");
		assertSame(ctx.getBean(AppProperties.class), consumer.getProperties(),
				"@Component should receive the same config properties instance");
	}
}
