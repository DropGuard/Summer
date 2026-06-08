package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.core.exception.MissingFieldException;
import summer.tck.AbstractContextTCK;
import summer.tck.di.configprops.*;
import summer.tck.di.missing.StrictConfig;

/**
 * TCK contract for {@code @ConfigurationProperties} auto-binding.
 *
 * <p>
 * Verifies that the DI engine:
 * <ul>
 * <li>Discovers {@code @ConfigurationProperties} records via Jandex</li>
 * <li>Binds YAML values to record components</li>
 * <li>Applies {@code @DefaultValue} when YAML field is absent</li>
 * <li>Throws {@link MissingFieldException} when required field is absent and
 * has no {@code @DefaultValue}</li>
 * <li>Registers the bound record as a singleton bean</li>
 * <li>Makes it injectable into {@code @Component} and {@code @Bean}
 * dependencies</li>
 * </ul>
 */
public abstract class AbstractConfigurationPropertiesTCK extends AbstractContextTCK {

	/**
	 * Creates a context with a specific entry point. Used by tests that need
	 * different YAML sections or entry classes.
	 */
	protected abstract ApplicationContext createContext(Class<?> entryPoint);

	/**
	 * Tests that require a valid YAML with all fields present.
	 */
	@Nested
	class ValidConfig {

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
			assertTrue(props.verbose(), "Boolean value from YAML should be bound");
		}

		@Test
		void testInjectableIntoBeanMethod() {
			ApplicationContext ctx = context();
			AppService service = ctx.getBean(AppService.class);
			assertNotNull(service, "@Bean that depends on config properties should be created");
			assertSame(ctx.getBean(AppProperties.class), service.getProperties(),
					"@Bean should receive the same config properties instance");
		}
	}

	/**
	 * Regression test: @ConfigurationProperties records with non-bean constructor
	 * params (Boolean, String, Integer) must NOT cause NoSuchBeanException when the
	 * dependency graph is built. These params are bound from YAML, not injected as
	 * beans.
	 */
	@Nested
	class NonBeanConstructorParams {

		@Test
		void testNonBeanConstructorParamsResolved() {
			ApplicationContext ctx = context();
			TlsProperties tls = ctx.getBean(TlsProperties.class);
			assertNotNull(tls, "@ConfigurationProperties with non-bean params should be registered");
			assertTrue(tls.enabled(), "Boolean value from YAML should be bound");
			assertEquals("/path/to/cert.pem", tls.certChain(), "String value from YAML should be bound");
			assertEquals(Integer.valueOf(8443), tls.port(), "Integer @DefaultValue should be applied");
		}
	}

	/**
	 * Tests for missing required fields (no @DefaultValue).
	 */
	@Nested
	class MissingField {

		@Test
		void testMissingRequiredFieldThrows() {
			assertThrows(MissingFieldException.class, () -> createContext(StrictConfig.class),
					"Field absent from YAML with no @DefaultValue should throw MissingFieldException");
		}
	}
}
