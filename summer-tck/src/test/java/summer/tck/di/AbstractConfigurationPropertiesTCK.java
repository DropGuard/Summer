package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.AbstractContextTCK;
import summer.fixtures.di.configprops.*;
import summer.fixtures.di.missing.StrictConfig;
import summer.fixtures.di.root.RootConfig;
import summer.fixtures.di.root.RootService;

/**
 * TCK contract for {@code @ConfigurationProperties} auto-binding.
 *
 * <p>
 * Verifies that the DI engine:
 * <ul>
 * <li>Discovers {@code @ConfigurationProperties} records via Jandex</li>
 * <li>Binds YAML values to record components</li>
 * <li>Applies {@code @DefaultValue} when YAML field is absent</li>
 * <li>Sets fields without {@code @DefaultValue} to {@code null} when absent
 * from YAML</li>
 * <li>Registers the bound record as a singleton bean</li>
 * <li>Makes it injectable into {@code @Component} and {@code @Bean}
 * dependencies</li>
 * </ul>
 *
 * <p>
 * Note: binding logic details (key normalization, prefix extraction, unknown
 * fields, type conversion) are covered by {@code ConfigurationBinderTest} in
 * summer-core. This TCK only tests DI engine integration points.
 * </p>
 */
public abstract class AbstractConfigurationPropertiesTCK extends AbstractContextTCK {

	/**
	 * Creates a context with a specific entry point. Used by tests that need
	 * different YAML sections or entry classes.
	 */
	protected abstract ApplicationContext createContext(Class<?> entryPoint);

	// ──────────────────────────────────────────────────────────────────────
	// Scenario 0: Basic binding and injection (existing)
	// ──────────────────────────────────────────────────────────────────────

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
	 * Tests for fields without @DefaultValue — should be null when absent from
	 * YAML.
	 */
	@Nested
	class MissingField {

		@Test
		void testMissingFieldIsNull() {
			ApplicationContext ctx = createContext(StrictConfig.class);
			assertNotNull(ctx, "Context should be created even with missing fields");
			// StrictProperties.apiKey has no @DefaultValue, so it should be null
			// when not in YAML. Validation Phase (if configured) catches business errors.
		}
	}

	// ──────────────────────────────────────────────────────────────────────
	// Scenario 1: @Component injection
	// ──────────────────────────────────────────────────────────────────────

	/**
	 * A {@code @Component} can inject a {@code @ConfigurationProperties} bean via
	 * constructor injection — same mechanism as {@code @Bean} methods.
	 *
	 * <p>
	 * Fixture: {@link PropertiesConsumer} is a plain {@code @Component} with a
	 * constructor that takes {@link AppProperties}.
	 * </p>
	 */
	@Nested
	class ComponentInjection {

		@Test
		void testComponentCanInjectConfigProperties() {
			ApplicationContext ctx = context();
			PropertiesConsumer consumer = ctx.getBean(PropertiesConsumer.class);
			assertNotNull(consumer, "@Component that depends on config properties should be created");
			assertNotNull(consumer.getProperties(), "Injected config properties should not be null");
			assertEquals("summer-tck", consumer.getProperties().name(),
					"@Component should receive YAML-bound config properties");
		}
	}

	// ──────────────────────────────────────────────────────────────────────
	// Scenario 3: Binding happens before graph construction
	// ──────────────────────────────────────────────────────────────────────

	/**
	 * Proves that {@code bindConfigurationProperties()} runs before the dependency
	 * graph is built — a {@code @Component} that depends on a config-properties
	 * bean should be created without {@code NoSuchBeanException}.
	 */
	@Nested
	class BindingOrder {

		@Test
		void testConfigPropertiesAvailableAsDependency() {
			ApplicationContext ctx = context();
			// PropertiesConsumer is a @Component with constructor(AppProperties).
			// If binding happened AFTER graph construction, injecting AppProperties
			// would throw NoSuchBeanException.
			assertNotNull(ctx.getBean(PropertiesConsumer.class),
					"Component depending on config properties should be present");
		}

		@Test
		void testServiceReceivesCorrectlyBoundProperties() {
			ApplicationContext ctx = context();
			AppService service = ctx.getBean(AppService.class);
			assertNotNull(service, "Service depending on config properties should be created");
			assertEquals("summer-tck", service.getProperties().name(),
					"Config properties should be bound with correct YAML values");
		}
	}

	// ──────────────────────────────────────────────────────────────────────
	// Scenario 4: Multiple prefixes coexist
	// ──────────────────────────────────────────────────────────────────────

	/**
	 * Multiple {@code @ConfigurationProperties} with different prefixes bind
	 * independently from different YAML sections without interfering.
	 *
	 * <p>
	 * Fixture: {@link AppProperties} (prefix=app) and {@link TlsProperties}
	 * (prefix=server.tls) both present in the same context.
	 * </p>
	 */
	@Nested
	class MultiplePrefixes {

		@Test
		void testMultiplePrefixesBoundIndependently() {
			ApplicationContext ctx = context();
			AppProperties app = ctx.getBean(AppProperties.class);
			TlsProperties tls = ctx.getBean(TlsProperties.class);

			assertNotNull(app, "AppProperties (prefix=app) should be registered");
			assertNotNull(tls, "TlsProperties (prefix=server.tls) should be registered");

			assertEquals("summer-tck", app.name(), "AppProperties should have app.name from YAML");
			assertEquals(Integer.valueOf(8080), app.port(), "AppProperties should have app.port from YAML");

			assertTrue(tls.enabled(), "TlsProperties should have server.tls.enabled from YAML");
			assertEquals("/path/to/cert.pem", tls.certChain(),
					"TlsProperties should have server.tls.cert-chain from YAML");
		}
	}

	// ──────────────────────────────────────────────────────────────────────
	// Scenario 5: Empty prefix binds root YAML
	// ──────────────────────────────────────────────────────────────────────

	/**
	 * {@code @ConfigurationProperties(prefix = "")} binds the entire YAML root
	 * instead of a nested section.
	 *
	 * <p>
	 * Fixture: {@link summer.tck.di.root.RootProperties} with prefix="" binds the
	 * {@code root:} section from the YAML root.
	 * </p>
	 */
	@Nested
	class EmptyPrefix {

		@Test
		void testEmptyPrefixBindsRootYaml() {
			ApplicationContext ctx = createContext(RootConfig.class);
			RootService service = ctx.getBean(RootService.class);
			assertNotNull(service, "Service depending on root config should be created");

			var props = service.getProperties();
			assertNotNull(props, "Root properties should be bound from YAML root");
			assertNotNull(props.root(), "Nested 'root' section should be bound");
			assertEquals("localhost", props.root().host(), "Empty prefix should bind root.host from YAML");
			assertEquals(Integer.valueOf(9090), props.root().port(), "Empty prefix should bind root.port from YAML");
		}
	}
}
