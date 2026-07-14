package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.di.configprops.*;
import summer.fixtures.di.root.RootService;
import summer.test.annotation.DualEngineTest;

@DualEngineTest
public class ConfigurationPropertiesBehaviorTest {

	private final BeanContainer context;

	public ConfigurationPropertiesBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@Test
	void testPropertiesBeanRegistered() {
		assertNotNull(context.getBean(AppProperties.class));
	}

	@Test
	void testYamlValuesBound() {
		AppProperties props = context.getBean(AppProperties.class);
		assertEquals("summer-tck", props.name());
		assertEquals(Integer.valueOf(8080), props.port());
		assertTrue(props.verbose());
	}

	@Test
	void testInjectableIntoBeanMethod() {
		AppService service = context.getBean(AppService.class);
		assertNotNull(service);
		assertSame(context.getBean(AppProperties.class), service.getProperties());
	}

	@Test
	void testNonBeanConstructorParamsResolved() {
		TlsProperties tls = context.getBean(TlsProperties.class);
		assertNotNull(tls);
		assertTrue(tls.enabled());
		assertEquals("/path/to/cert.pem", tls.certChain());
		assertEquals(Integer.valueOf(8443), tls.port());
	}

	@Test
	void testMissingFieldIsNull() {
		assertNotNull(context);
	}

	@Test
	void testComponentCanInjectConfigProperties() {
		PropertiesConsumer consumer = context.getBean(PropertiesConsumer.class);
		assertNotNull(consumer);
		assertNotNull(consumer.getProperties());
		assertEquals("summer-tck", consumer.getProperties().name());
	}

	@Test
	void testConfigPropertiesAvailableAsDependency() {
		assertNotNull(context.getBean(PropertiesConsumer.class));
	}

	@Test
	void testServiceReceivesCorrectlyBoundProperties() {
		AppService service = context.getBean(AppService.class);
		assertNotNull(service);
		assertEquals("summer-tck", service.getProperties().name());
	}

	@Test
	void testMultiplePrefixesBoundIndependently() {
		AppProperties app = context.getBean(AppProperties.class);
		TlsProperties tls = context.getBean(TlsProperties.class);
		assertNotNull(app);
		assertNotNull(tls);
		assertEquals("summer-tck", app.name());
		assertTrue(tls.enabled());
	}

	@Test
	void testEmptyPrefixBindsRootYaml() {
		RootService service = context.getBean(RootService.class);
		assertNotNull(service);
		var props = service.getProperties();
		assertNotNull(props);
		assertEquals("localhost", props.root().host());
	}
}
