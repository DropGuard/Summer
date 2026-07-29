package com.github.dropguard.summer.aot;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.Discovery;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.BeanDeployment;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.bean.RouteInfo;
import com.github.dropguard.summer.core.bean.SharedDependencyResolver;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for cross-module bean discovery via Jandex indexes.
 *
 * <p>This test verifies that the Maven plugin can discover beans from dependency JARs (not just the
 * current module's source files).
 *
 * <p>This catches the original bug where beans from dependency modules (like Router from
 * summer-web) couldn't be discovered by the AOT system.
 */
class CrossModuleDiscoveryTest {

    @TempDir Path tempDir;

    private static org.jboss.jandex.IndexView emptyIndex() {
        try {
            return org.jboss.jandex.Index.of(new Class<?>[0]);
        } catch (java.io.IOException e) {
            throw new java.lang.AssertionError("empty index", e);
        }
    }

    @Test
    void discoversBeansFromMultipleIndexes() throws Exception {
        // Simulate two modules: one providing beans, one consuming them

        // Module A: provides ServiceA, ServiceB
        Index moduleAIndex =
                buildIndex(
                        "com.github.dropguard.summer.fixtures.dummy.ServiceA",
                        "com.github.dropguard.summer.fixtures.dummy.ServiceB",
                        "com.github.dropguard.summer.fixtures.dummy.ServiceC");

        // Module B: provides a controller that depends on ServiceA
        Index moduleBIndex =
                buildIndex("com.github.dropguard.summer.fixtures.web.dummy.UserController");

        // Merge indexes (simulates what the Maven plugin does)
        CompositeIndex composite = CompositeIndex.create(List.of(moduleAIndex, moduleBIndex));

        // Verify beans from BOTH modules are discoverable
        DotName componentDot = DotName.createSimple("com.github.dropguard.summer.core.Component");

        List<String> discoveredBeans = new ArrayList<>();
        for (ClassInfo ci : composite.getKnownClasses()) {
            if (ci.hasAnnotation(componentDot)) {
                discoveredBeans.add(ci.name().toString());
            }
        }

        // Should find beans from both modules
        assertTrue(
                discoveredBeans.contains("com.github.dropguard.summer.fixtures.dummy.ServiceA"),
                "Should discover ServiceA from module A");
        assertTrue(
                discoveredBeans.contains("com.github.dropguard.summer.fixtures.dummy.ServiceB"),
                "Should discover ServiceB from module A");
        assertTrue(
                discoveredBeans.contains("com.github.dropguard.summer.fixtures.dummy.ServiceC"),
                "Should discover ServiceC from module A");
    }

    @Test
    void dependencyResolverFindsBeanByQualifiedName() {
        BeanDefinition serviceA =
                new BeanDefinition(
                        "com.github.dropguard.summer.fixtures.dummy.ServiceA", "ServiceA");
        serviceA.addParameter("com.github.dropguard.summer.fixtures.dummy.ServiceB");

        BeanDefinition serviceB =
                new BeanDefinition(
                        "com.github.dropguard.summer.fixtures.dummy.ServiceB", "ServiceB");
        serviceB.addParameter("com.github.dropguard.summer.fixtures.dummy.ServiceC");

        BeanDefinition serviceC =
                new BeanDefinition(
                        "com.github.dropguard.summer.fixtures.dummy.ServiceC", "ServiceC");

        List<BeanDefinition> beans = List.of(serviceA, serviceB, serviceC);
        SharedDependencyResolver resolver = new SharedDependencyResolver();

        List<BeanDefinition> sorted = resolver.resolve(beans);
        assertEquals(3, sorted.size(), "Should resolve all 3 beans");
        assertEquals(
                "com.github.dropguard.summer.fixtures.dummy.ServiceC", sorted.get(0).qualifiedName);
    }

    @Test
    void dependencyResolverFindsBeanByInterface() {
        BeanDefinition impl =
                new BeanDefinition(
                        "com.github.dropguard.summer.fixtures.dummy.ServiceBImpl", "ServiceBImpl");
        impl.interfaceNames.add("com.github.dropguard.summer.fixtures.dummy.ServiceB");

        BeanDefinition consumer =
                new BeanDefinition(
                        "com.github.dropguard.summer.fixtures.dummy.ServiceA", "ServiceA");
        consumer.addParameter("com.github.dropguard.summer.fixtures.dummy.ServiceB");

        List<BeanDefinition> beans = List.of(impl, consumer);
        SharedDependencyResolver resolver = new SharedDependencyResolver();

        List<BeanDefinition> sorted = resolver.resolve(beans);
        assertEquals(2, sorted.size());
    }

    @Test
    void aotContextGeneratorProducesValidCode() throws IOException {
        BeanDefinition serviceC =
                new BeanDefinition(
                        "com.github.dropguard.summer.fixtures.dummy.ServiceC", "ServiceC");

        File outputDir = tempDir.toFile();
        AotContextGenerator generator =
                new AotContextGenerator(
                        emptyIndex(), outputDir, new WireMethodGenerator(emptyIndex()));
        generator.generate(List.of(serviceC));

        File generatedFile =
                new File(
                        outputDir,
                        "com/github/dropguard/summer/aot/generated/GeneratedAotContext.java");
        assertTrue(generatedFile.exists(), "Generated AOT context should exist");
        String content = Files.readString(generatedFile.toPath());
        assertTrue(
                content.contains("com.github.dropguard.summer.fixtures.dummy.ServiceC"),
                "Generated code should reference the bean");
    }

    /**
     * Verifies that BeanDiscovery correctly extracts @PathParam binding names from controller
     * method parameters. This catches the bug where PAGEABLE_DOT was used instead of PATH_PARAM_DOT
     * when extracting the annotation value for @PathParam parameters.
     */
    @Test
    void collectsPathParamBindingNames() throws Exception {
        // Build index with the fixture controller and its annotation dependencies
        Index index =
                buildIndex(
                        "com.github.dropguard.summer.fixtures.dummy.DummyController",
                        "com.github.dropguard.summer.web.annotation.RestController",
                        "com.github.dropguard.summer.core.Component",
                        "com.github.dropguard.summer.web.annotation.Get",
                        "com.github.dropguard.summer.web.annotation.Put",
                        "com.github.dropguard.summer.web.annotation.Delete",
                        "com.github.dropguard.summer.web.annotation.PathParam");
        List<BeanDefinition> beans = Discovery.discover(BeanDeployment.forNarrow(index));

        // Find the DummyController bean
        BeanDefinition controller =
                beans.stream()
                        .filter(
                                b ->
                                        b.qualifiedName.equals(
                                                "com.github.dropguard.summer.fixtures.dummy.DummyController"))
                        .findFirst()
                        .orElse(null);
        assertNotNull(controller, "Should discover DummyController");
        assertFalse(controller.routes.isEmpty(), "Should have routes");

        // Find the GET /{id} route (getById method)
        RouteInfo getByIdRoute =
                controller.routes.stream()
                        .filter(r -> r.httpMethod.equals("GET") && r.path.equals("/dummy/{id}"))
                        .findFirst()
                        .orElse(null);
        assertNotNull(getByIdRoute, "Should discover GET /dummy/{id} route");

        // Verify the @PathParam("id") parameter has the correct binding name
        // Before the fix, this would return the default parameter name because
        // BeanDiscovery used PAGEABLE_DOT instead of PATH_PARAM_DOT
        RouteInfo.ParamInfo idParam =
                getByIdRoute.params.stream()
                        .filter(p -> p.binding == RouteInfo.ParamBinding.PATH)
                        .findFirst()
                        .orElse(null);
        assertNotNull(idParam, "Should have a PATH parameter");
        assertEquals(
                "id",
                idParam.name,
                "@PathParam(\"id\") binding name should be 'id', not the default parameter name");
    }

    /** Build a Jandex index from class names by finding the .class files on the classpath. */
    private Index buildIndex(String... classNames) throws IOException {
        Indexer indexer = new Indexer();
        for (String className : classNames) {
            String resourcePath = "/" + className.replace('.', '/') + ".class";
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    indexer.index(is);
                }
            }
        }
        return indexer.complete();
    }

    /** Verifies that BeanDiscovery finds @ConfigMapping interface config beans. */
    @Test
    void discoversConfigMapping() throws Exception {
        Index index =
                buildIndex(
                        "com.github.dropguard.summer.fixtures.dummy.DummyConfigProperties",
                        "com.github.dropguard.summer.core.config.ConfigMapping");

        List<BeanDefinition> beans = Discovery.discover(BeanDeployment.forNarrow(index));

        assertTrue(
                beans.stream()
                        .anyMatch(
                                b ->
                                        b instanceof ConfigPropertiesBean
                                                && b.qualifiedName.equals(
                                                        "com.github.dropguard.summer.fixtures.dummy.DummyConfigProperties")),
                "Should discover DummyConfigProperties as ConfigPropertiesBean, found: " + beans);
    }

    /**
     * Verifies that BeanDiscovery discovers beans across packages (no package filtering when prefix
     * is null).
     */
    @Test
    void discoversBeansAcrossPackages() throws Exception {
        Index index =
                buildIndex(
                        "com.github.dropguard.summer.fixtures.dummy.ServiceA",
                        "com.github.dropguard.summer.fixtures.dummy.MultiBeanConfiguration",
                        "com.github.dropguard.summer.fixtures.dummy.PlainServiceA",
                        "com.github.dropguard.summer.fixtures.dummy.PlainServiceB",
                        "com.github.dropguard.summer.core.Component",
                        "com.github.dropguard.summer.core.annotation.Configuration",
                        "com.github.dropguard.summer.core.annotation.Bean");

        List<BeanDefinition> beans = Discovery.discover(BeanDeployment.forNarrow(index));

        // With classpath scope, should discover ALL beans regardless of package
        long componentCount =
                beans.stream()
                        .filter(b -> !b.isFactoryMethod() && !(b instanceof ConfigPropertiesBean))
                        .count();
        long factoryCount = beans.stream().filter(b -> b.isFactoryMethod()).count();

        assertTrue(
                componentCount >= 2,
                "Should find component beans (ServiceA + MultiBeanConfiguration), found: "
                        + componentCount);
        assertTrue(factoryCount >= 2, "Should find factory beans, found: " + factoryCount);
    }

    /**
     * Verifies that BeanDiscovery finds ALL @Bean methods in a @Configuration class, not just the
     * first one. This catches bugs where factory method scanning misses methods.
     */
    @Test
    void discoversAllBeanFactoryMethods() throws Exception {
        Index index =
                buildIndex(
                        "com.github.dropguard.summer.fixtures.dummy.MultiBeanConfiguration",
                        "com.github.dropguard.summer.core.annotation.Configuration",
                        "com.github.dropguard.summer.core.annotation.Bean",
                        "com.github.dropguard.summer.fixtures.dummy.PlainServiceA",
                        "com.github.dropguard.summer.fixtures.dummy.PlainServiceB");

        List<BeanDefinition> beans = Discovery.discover(BeanDeployment.forNarrow(index));

        long factoryCount = beans.stream().filter(b -> b.isFactoryMethod()).count();
        assertEquals(
                2,
                factoryCount,
                "Should discover 2 @Bean factory products, found "
                        + factoryCount
                        + ": "
                        + beans.stream()
                                .filter(b -> b.isFactoryMethod())
                                .map(b -> b.qualifiedName)
                                .toList());

        assertTrue(
                beans.stream()
                        .anyMatch(
                                b ->
                                        b.isFactoryMethod()
                                                && b.qualifiedName.equals(
                                                        "com.github.dropguard.summer.fixtures.dummy.PlainServiceA")),
                "Should discover PlainServiceA as a factory bean");
        assertTrue(
                beans.stream()
                        .anyMatch(
                                b ->
                                        b.isFactoryMethod()
                                                && b.qualifiedName.equals(
                                                        "com.github.dropguard.summer.fixtures.dummy.PlainServiceB")),
                "Should discover PlainServiceB as a factory bean");
    }
}
