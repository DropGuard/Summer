package com.github.dropguard.summer.aot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.aot.testfixtures.Consumer;
import com.github.dropguard.summer.aot.testfixtures.Dep;
import com.github.dropguard.summer.aot.testfixtures.NumericConfig;
import com.github.dropguard.summer.aot.testfixtures.Service;
import com.github.dropguard.summer.aot.testfixtures.ServiceA;
import com.github.dropguard.summer.aot.testfixtures.ServiceB;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.InjectionParameter;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeSpec;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link WireMethodGenerator}'s constructor-argument emission reads each parameter's
 * own resolved list directly — no positional cursor, no re-filtering by element type. The key
 * regression this locks in: two {@code List<T>} parameters of the SAME element type must keep their
 * own dependency slices, which the old cursor+filter approach could not guarantee.
 */
class WireMethodGeneratorTest {

    private final WireMethodGenerator generator = new WireMethodGenerator(emptyIndex());

    private static org.jboss.jandex.IndexView emptyIndex() {
        try {
            return org.jboss.jandex.Index.of(new Class<?>[0]);
        } catch (java.io.IOException e) {
            throw new java.lang.AssertionError("empty index", e);
        }
    }

    private CodeBlock buildArgs(BeanDefinition bean) throws Exception {
        Method m =
                WireMethodGenerator.class.getDeclaredMethod(
                        "buildConstructorArgs", BeanDefinition.class);
        m.setAccessible(true);
        return (CodeBlock) m.invoke(generator, bean);
    }

    private BeanDefinition dep(String name) {
        BeanDefinition d = new BeanDefinition(name, name.substring(name.lastIndexOf('.') + 1));
        d.variableName = Character.toLowerCase(d.simpleName.charAt(0)) + d.simpleName.substring(1);
        return d;
    }

    @Test
    void twoListsOfSameElementTypeKeepDistinctSlices() throws Exception {
        BeanDefinition svcA = dep(ServiceA.class.getName());
        BeanDefinition svcB = dep(ServiceB.class.getName());

        BeanDefinition consumer = new BeanDefinition(Consumer.class.getName(), "Consumer");
        // first List<Service> carries svcA, second List<Service> carries svcB
        InjectionParameter first =
                new InjectionParameter(
                        "java.util.List<" + Service.class.getName() + ">",
                        new ArrayList<>(List.of(svcA)));
        InjectionParameter second =
                new InjectionParameter(
                        "java.util.List<" + Service.class.getName() + ">",
                        new ArrayList<>(List.of(svcB)));
        consumer.parameters.add(first);
        consumer.parameters.add(second);

        String args = buildArgs(consumer).toString();
        // Each List emits its own slice: [svcA], [svcB] — not both lists getting
        // the union, which a by-element-type re-filter would produce.
        assertEquals("java.util.List.of(serviceA), java.util.List.of(serviceB)", args);
    }

    @Test
    void emptyResolvedListRescansBuilderAtRuntime() throws Exception {
        // Mirrors the runtime engine (BeanInstantiator): an empty resolved list is re-scanned from
        // the builder at runtime — for a mocked element type this yields the single mock instead of
        // silently dropping it (List<MockedType> divergence fix).
        BeanDefinition consumer = new BeanDefinition(Consumer.class.getName(), "Consumer");
        consumer.parameters.add(
                new InjectionParameter(
                        "java.util.List<" + Service.class.getName() + ">", new ArrayList<>()));

        assertEquals(
                "builder.getBeans(com.github.dropguard.summer.aot.testfixtures.Service.class)",
                buildArgs(consumer).toString());
    }

    @Test
    void beanContainerScalarIsRejected() throws Exception {
        // Mirrors the runtime engine (BeanInstantiator): injecting the container into a bean
        // would create a circular bootstrap reference — codegen must reject it, not emit null.
        BeanDefinition consumer = new BeanDefinition(Consumer.class.getName(), "Consumer");
        consumer.parameters.add(
                new InjectionParameter(
                        "com.github.dropguard.summer.core.BeanContainer", new ArrayList<>()));

        com.github.dropguard.summer.core.exception.BeanCreationException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.github.dropguard.summer.core.exception.BeanCreationException.class,
                        () -> {
                            try {
                                buildArgs(consumer);
                            } catch (java.lang.reflect.InvocationTargetException e) {
                                // reflective helper wraps the real exception
                                throw (RuntimeException) e.getCause();
                            }
                        });
        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().contains("ApplicationContext injection is not supported"));
    }

    @Test
    void scalarDependencyEmitsVariableName() throws Exception {
        BeanDefinition depBean = dep(Dep.class.getName());
        BeanDefinition consumer = new BeanDefinition(Consumer.class.getName(), "Consumer");
        consumer.parameters.add(
                new InjectionParameter(Dep.class.getName(), new ArrayList<>(List.of(depBean))));

        assertEquals("dep", buildArgs(consumer).toString());
    }

    /**
     * Regression for the AOT config-binding ClassCast bug: a {@code long} (or any primitive
     * numeric) config field must be coerced through {@code TypeConverter} at the generated call
     * site, not via a bare {@code (long)} cast that throws {@code ClassCastException: Integer
     * cannot be cast to Long} when the resolved section value is a {@code Number}.
     */
    @Test
    void numericConfigFieldUsesTypeConverterNotPrimitiveCast() throws Exception {
        Index index = Index.of(NumericConfig.class);
        ClassInfo classInfo =
                index.getClassByName(DotName.createSimple(NumericConfig.class.getName()));
        Method m =
                ConfigImplGenerator.class.getDeclaredMethod(
                        "generateConfigImpl", ClassName.class, ClassInfo.class);
        m.setAccessible(true);
        TypeSpec impl =
                (TypeSpec)
                        m.invoke(
                                new ConfigImplGenerator(emptyIndex()),
                                ClassName.get(NumericConfig.class),
                                classInfo);

        String generated = impl.toString();
        // The long/int/double fields must route through TypeConverter (which coerces a Number).
        org.junit.jupiter.api.Assertions.assertTrue(
                generated.contains("TypeConverter.convert"),
                "numeric config fields must use TypeConverter, but generated:\n" + generated);
        // And must NOT emit a bare primitive cast that would ClassCast an Integer to Long.
        org.junit.jupiter.api.Assertions.assertFalse(
                generated.contains("(long) __section.get")
                        || generated.contains("(int) __section.get")
                        || generated.contains("(double) __section.get"),
                "numeric config fields must not use a bare primitive cast, but generated:\n"
                        + generated);
    }
}
