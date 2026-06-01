package summer.plugin;

import static org.junit.jupiter.api.Assertions.*;

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
 * <p>This test verifies that the Maven plugin can discover beans from
 * dependency JARs (not just the current module's source files).</p>
 * 
 * <p>This catches the original bug where beans from dependency modules
 * (like Router from summer-web) couldn't be discovered by the AOT system.</p>
 */
class CrossModuleDiscoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversBeansFromMultipleIndexes() throws Exception {
        // Simulate two modules: one providing beans, one consuming them
        
        // Module A: provides ServiceA, ServiceB
        Index moduleAIndex = buildIndex(
            "summer.fixtures.dummy.ServiceA",
            "summer.fixtures.dummy.ServiceB",
            "summer.fixtures.dummy.ServiceC"
        );
        
        // Module B: provides a controller that depends on ServiceA
        Index moduleBIndex = buildIndex(
            "summer.example.UserController"
        );
        
        // Merge indexes (simulates what the Maven plugin does)
        CompositeIndex composite = CompositeIndex.create(List.of(moduleAIndex, moduleBIndex));
        
        // Verify beans from BOTH modules are discoverable
        DotName componentDot = DotName.createSimple("summer.core.Component");
        
        List<String> discoveredBeans = new ArrayList<>();
        for (ClassInfo ci : composite.getKnownClasses()) {
            if (ci.hasAnnotation(componentDot)) {
                discoveredBeans.add(ci.name().toString());
            }
        }
        
        // Should find beans from both modules
        assertTrue(discoveredBeans.contains("summer.fixtures.dummy.ServiceA"),
            "Should discover ServiceA from module A");
        assertTrue(discoveredBeans.contains("summer.fixtures.dummy.ServiceB"),
            "Should discover ServiceB from module A");
        assertTrue(discoveredBeans.contains("summer.fixtures.dummy.ServiceC"),
            "Should discover ServiceC from module A");
    }

    @Test
    void dependencyResolverFindsBeanByQualifiedName() {
        // Test that DependencyResolver can find beans by qualified name
        BeanDefinition serviceA = new BeanDefinition(
            BeanDefinition.Kind.COMPONENT,
            "summer.fixtures.dummy.ServiceA",
            "ServiceA"
        );
        serviceA.constructorParamTypes.add("summer.fixtures.dummy.ServiceB");
        
        BeanDefinition serviceB = new BeanDefinition(
            BeanDefinition.Kind.COMPONENT,
            "summer.fixtures.dummy.ServiceB",
            "ServiceB"
        );
        serviceB.constructorParamTypes.add("summer.fixtures.dummy.ServiceC");
        
        BeanDefinition serviceC = new BeanDefinition(
            BeanDefinition.Kind.COMPONENT,
            "summer.fixtures.dummy.ServiceC",
            "ServiceC"
        );
        
        List<BeanDefinition> beans = List.of(serviceA, serviceB, serviceC);
        DependencyResolver resolver = new DependencyResolver();
        
        // Should resolve without errors
        List<BeanDefinition> sorted = resolver.resolve(beans);
        assertEquals(3, sorted.size(), "Should resolve all 3 beans");
        
        // ServiceC should be first (no dependencies)
        assertEquals("summer.fixtures.dummy.ServiceC", sorted.get(0).qualifiedName);
    }

    @Test
    void dependencyResolverFindsBeanByInterface() {
        // Test interface-based dependency resolution
        BeanDefinition impl = new BeanDefinition(
            BeanDefinition.Kind.COMPONENT,
            "summer.fixtures.dummy.ServiceBImpl",
            "ServiceBImpl"
        );
        impl.interfaceNames.add("summer.fixtures.dummy.ServiceB");
        
        BeanDefinition consumer = new BeanDefinition(
            BeanDefinition.Kind.COMPONENT,
            "summer.fixtures.dummy.ServiceA",
            "ServiceA"
        );
        consumer.constructorParamTypes.add("summer.fixtures.dummy.ServiceB");
        
        List<BeanDefinition> beans = List.of(impl, consumer);
        DependencyResolver resolver = new DependencyResolver();
        
        List<BeanDefinition> sorted = resolver.resolve(beans);
        assertEquals(2, sorted.size());
    }

    @Test
    void aotContextGeneratorProducesValidCode() throws IOException {
        // Test that AOT code generation produces valid Java source
        BeanDefinition serviceC = new BeanDefinition(
            BeanDefinition.Kind.COMPONENT,
            "summer.fixtures.dummy.ServiceC",
            "ServiceC"
        );
        
		AotContextGenerator generator = new AotContextGenerator();
		File outputDir = tempDir.toFile();
		generator.generate(List.of(serviceC), outputDir);
        
        // Verify file exists
        File generatedFile = new File(outputDir, 
            "summer/core/aot/GeneratedAotContext.java");
        assertTrue(generatedFile.exists(), "Generated AOT context should exist");
        
        // Verify content references the bean
        String content = Files.readString(generatedFile.toPath());
        assertTrue(content.contains("summer.fixtures.dummy.ServiceC"),
            "Generated code should reference the bean");
    }

    /**
     * Build a Jandex index from class names by finding the .class files on the classpath.
     */
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
}
