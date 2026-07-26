package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.configprops.*;
import com.github.dropguard.summer.fixtures.di.root.RootService;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class ConfigurationPropertiesBehaviorTest {

	private final BeanContainer context;

	public ConfigurationPropertiesBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@DualEngine
	void testPropertiesBeanRegistered() {
		assertNotNull(context.getBean(AppProperties.class));
	}

	@DualEngine
	void testYamlValuesBound() {
		AppProperties props = context.getBean(AppProperties.class);
		assertEquals("summer-tck", props.name());
		assertEquals(Integer.valueOf(8080), props.port());
		assertTrue(props.verbose());
	}

	@DualEngine
	void testInjectableIntoBeanMethod() {
		AppService service = context.getBean(AppService.class);
		assertNotNull(service);
		assertSame(context.getBean(AppProperties.class), service.getProperties());
	}

	@DualEngine
	void testNonBeanConstructorParamsResolved() {
		TlsProperties tls = context.getBean(TlsProperties.class);
		assertNotNull(tls);
		assertTrue(tls.enabled());
		assertEquals("/path/to/cert.pem", tls.certChain());
		assertEquals(Integer.valueOf(8443), tls.port());
	}

	@DualEngine
	void testMissingFieldIsNull() {
		assertNotNull(context);
	}

	@DualEngine
	void testComponentCanInjectConfigProperties() {
		PropertiesConsumer consumer = context.getBean(PropertiesConsumer.class);
		assertNotNull(consumer);
		assertNotNull(consumer.getProperties());
		assertEquals("summer-tck", consumer.getProperties().name());
	}

	@DualEngine
	void testConfigPropertiesAvailableAsDependency() {
		assertNotNull(context.getBean(PropertiesConsumer.class));
	}

	@DualEngine
	void testServiceReceivesCorrectlyBoundProperties() {
		AppService service = context.getBean(AppService.class);
		assertNotNull(service);
		assertEquals("summer-tck", service.getProperties().name());
	}

	@DualEngine
	void testMultiplePrefixesBoundIndependently() {
		AppProperties app = context.getBean(AppProperties.class);
		TlsProperties tls = context.getBean(TlsProperties.class);
		assertNotNull(app);
		assertNotNull(tls);
		assertEquals("summer-tck", app.name());
		assertTrue(tls.enabled());
	}

	@DualEngine
	void testEmptyPrefixBindsRootYaml() {
		RootService service = context.getBean(RootService.class);
		assertNotNull(service);
		var props = service.getProperties();
		assertNotNull(props);
		assertEquals("localhost", props.root().host());
	}
}
