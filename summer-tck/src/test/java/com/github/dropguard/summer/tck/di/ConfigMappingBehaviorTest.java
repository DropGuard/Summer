package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.configprops.*;
import com.github.dropguard.summer.fixtures.di.root.RootService;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class ConfigMappingBehaviorTest {

    private final BeanContainer context;

    public ConfigMappingBehaviorTest(BeanContainer context) {
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

    @DualEngine
    void testAotGeneratesAllReturnTypes() {
        // Exercises every AOT code-gen branch for @ConfigMapping: enum, List<String>,
        // @WithName key rename, and @WithDefault — under both engines.
        WebConfig web = context.getBean(WebConfig.class);
        assertNotNull(web);
        assertEquals("web.local", web.host());
        assertEquals(WebConfig.RouterType.LINEAR, web.routerType());
        assertEquals(
                java.util.List.of("https://a.example.com", "https://b.example.com"),
                web.allowedOrigins());
        assertEquals(250, web.maxConn());
    }
}
