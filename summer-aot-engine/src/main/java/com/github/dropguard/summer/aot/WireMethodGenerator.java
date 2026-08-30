package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.bean.InjectionParameter;
import com.github.dropguard.summer.engine.spi.AotProductConstructor;
import com.github.dropguard.summer.engine.spi.AotProductConstructors;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/**
 * Generates the bean instantiation body of the AOT-created {@code create()} method. Emits {@code
 * builder.register(...)} calls for each bean.
 *
 * <p>Facade over the codegen pipeline: constructor injection / {@code @Bean} invocation / AOP proxy
 * wrapping / interface registration are emitted here; {@code @ConfigMapping} binding is delegated
 * to {@link ConfigImplGenerator}; {@code @RowModel} row mappers are registered by the owning module
 * via {@link AotProductConstructor}'s post-construction statements (see the data-jdbc
 * implementation), mirroring the runtime engine's assembly-time filler bean.
 */
@Internal
public final class WireMethodGenerator {

    private final IndexView index;
    private final ConfigImplGenerator configGen;

    public WireMethodGenerator(IndexView index) {
        this.index = index;
        this.configGen = new ConfigImplGenerator(index);
    }

    private void emitComponentInstantiation(
            MethodSpec.Builder wire, BeanDefinition bean, ClassName beanClass, String varName) {
        CodeBlock args = buildConstructorArgs(bean);
        // Summer does not support class-based proxying -- JDK dynamic proxy
        // requires at least one interface. Fail fast.
        if (bean.needsProxy() && bean.interfaceNames.isEmpty()) {
            throw new com.github.dropguard.summer.aop.SummerAopException(
                    com.github.dropguard.summer.core.ErrorCode.AOP_NO_INTERFACE,
                    bean.qualifiedName
                            + " is annotated with AOP bindings but implements no interfaces. Summer"
                            + " uses JDK dynamic proxies -- extract an interface and inject it by"
                            + " the interface type instead.");
        }
        if (bean.needsProxy()) {
            String implVar = varName + "_impl";
            if (bean.parameters.isEmpty()) {
                wire.addStatement("$T $N = new $T()", beanClass, implVar, beanClass);
            } else {
                wire.addStatement("$T $N = new $T($L)", beanClass, implVar, beanClass, args);
            }
            // @PostConstruct runs on the raw instance before the proxy wraps it — lifecycle
            // callbacks are never intercepted (CDI semantics).
            emitPostConstruct(wire, bean, implVar);

            String interceptorsListVar = varName + "_interceptors";
            wire.addStatement(
                    "$T<$T> $N = new $T<>()",
                    ClassName.get(List.class),
                    ClassName.get("com.github.dropguard.summer.aop", "MethodInterceptor"),
                    interceptorsListVar,
                    ClassName.get(ArrayList.class));

            for (BeanDefinition interceptor : bean.interceptors) {
                wire.addStatement("$N.add($N)", interceptorsListVar, interceptor.variableName);
            }

            com.palantir.javapoet.TypeName proxyType =
                    bean.interfaceNames.isEmpty()
                            ? beanClass
                            : AotTypeNames.safeClassName(bean.interfaceNames.get(0));
            ClassName proxyClass =
                    ClassName.get(beanClass.packageName(), beanClass.simpleName() + "$$AotProxy");
            wire.addStatement(
                    "$T $N = new $T($N, $N)",
                    proxyType,
                    varName,
                    proxyClass,
                    implVar,
                    interceptorsListVar);
        } else {
            if (bean.parameters.isEmpty()) {
                wire.addStatement("$T $N = new $T()", beanClass, varName, beanClass);
            } else {
                wire.addStatement("$T $N = new $T($L)", beanClass, varName, beanClass, args);
            }
            emitPostConstruct(wire, bean, varName);
        }
    }

    /**
     * Emits the {@code @PostConstruct} lifecycle call on the raw instance, immediately after
     * construction — the generated counterpart of {@code BeanInstantiator.invokePostConstruct}.
     *
     * <p>The call is wrapped so a throwing callback surfaces as a {@link BeanCreationException}
     * naming the bean, mirroring the runtime engine's message — otherwise the generated direct call
     * would propagate the raw exception and the two engines would report the same failure
     * differently. Catching {@code Throwable} mirrors the runtime's unwrap-to-the-bean's-own-cause
     * behaviour (the bean may throw an {@code Error}, and the runtime reports it as the cause).
     */
    private static void emitPostConstruct(
            MethodSpec.Builder wire, BeanDefinition bean, String varName) {
        if (bean.postConstructMethod == null) {
            return;
        }
        wire.beginControlFlow("try");
        wire.addStatement("$N.$N()", varName, bean.postConstructMethod);
        wire.nextControlFlow("catch (java.lang.Throwable t)");
        wire.addStatement(
                "throw new $T($S, t)",
                com.github.dropguard.summer.core.exception.BeanCreationException.class,
                "Failed to invoke @PostConstruct on bean: " + bean.qualifiedName);
        wire.endControlFlow();
    }

    private CodeBlock buildConstructorArgs(BeanDefinition bean) {
        CodeBlock.Builder args = CodeBlock.builder();
        boolean first = true;
        // Each parameter carries its own resolved dependencies (populated by
        // SharedDependencyResolver). Position is the list index, so no cursor
        // rebuilds the structure — we read parameters directly.
        for (InjectionParameter parameter : bean.parameters) {
            if (!first) args.add(", ");
            first = false;
            args.add(parameterArgument(parameter));
        }
        return args.build();
    }

    /**
     * Builds the constructor/method argument expression for one injection parameter. A List<T>
     * emits {@code List.of(dep1, dep2, ...)} straight from the parameter's own resolved list — no
     * re-filtering by element type that could collide when two List<T> parameters share an element
     * type.
     */
    private CodeBlock parameterArgument(InjectionParameter parameter) {
        if (parameter.typeName().startsWith("java.util.List<")) {
            if (parameter.resolved().isEmpty()) {
                // Mirror the runtime engine (BeanInstantiator): an empty resolved list is
                // re-scanned from the builder at runtime — for a mocked element type this yields
                // the single mock (registered before the wire loop), exactly like the scalar mock
                // path. A bare java.util.List.of() would silently drop the mock, diverging from
                // Runtime on List<MockedType> injection.
                String elementType =
                        parameter
                                .typeName()
                                .substring(
                                        "java.util.List<".length(),
                                        parameter.typeName().length() - 1);
                return CodeBlock.of(
                        "builder.getBeans($T.class)", AotTypeNames.safeClassName(elementType));
            }
            CodeBlock.Builder cb = CodeBlock.builder();
            cb.add("java.util.List.of(");
            boolean first = true;
            for (BeanDefinition dep : parameter.resolved()) {
                if (!first) cb.add(", ");
                first = false;
                cb.add("$N", dep.variableName);
            }
            cb.add(")");
            return cb.build();
        }
        // Scalar (non-List) parameter. BeanContainer injection is rejected in
        // SharedDependencyResolver at discovery time (every BeanDefinition passes through it), so
        // no scalar parameter here is ever a BeanContainer.
        if (parameter.resolved().isEmpty()) {
            // A dependency on a mocked type: its bean definition was removed at discovery and
            // the mock instance is registered on the builder before the wire loop runs
            // (build(Object...) mock branch). Resolve through getBean so the generated code
            // shares the RUNTIME engine's assignability scan — a @Mock declared on the concrete
            // type must satisfy consumers injecting supertypes or transitive interfaces, and a
            // genuinely missing dependency fails loudly instead of injecting null.
            ClassName paramClass = AotTypeNames.safeClassName(parameter.typeName());
            return CodeBlock.of("($T) builder.getBean($T.class)", paramClass, paramClass);
        }
        return CodeBlock.of("$N", parameter.resolved().get(0).variableName);
    }

    private void emitFactoryProductInstantiation(
            MethodSpec.Builder wire, BeanDefinition bean, String varName) {
        ClassName producedClass = AotTypeNames.safeClassName(bean.qualifiedName);

        // Custom construction for @Bean products the generic generator cannot derive: the owning
        // module supplies the expression via the AotProductConstructor SPI (e.g. data-jdbc's
        // EntityMetadataRegistrar, whose declared constructor takes the IndexView that the AOT
        // container deliberately never materializes). Emitted verbatim, like aotInstanceExpression.
        // Resolved along the product's supertype chain so a subclass product inherits its base
        // type's provider (e.g. data-jdbc's JdbcTemplateAotConstructor fills mappers on any
        // JdbcTemplate-typed product) — mirroring the runtime engine, whose assignability-based
        // lookup already covers subclasses.
        AotProductConstructor provider = resolveProvider(bean);
        String construction = provider != null ? provider.construction(bean, index) : null;

        String configVar = bean.configBeanDefinition.variableName;
        String methodName = bean.producerMethodName;
        CodeBlock args = buildConstructorArgs(bean);

        if (construction != null) {
            wire.addStatement("$T $N = $L", producedClass, varName, construction);
        } else if (bean.parameters.isEmpty()) {
            wire.addStatement("$T $N = $N.$N()", producedClass, varName, configVar, methodName);
        } else {
            wire.addStatement(
                    "$T $N = $N.$N($L)", producedClass, varName, configVar, methodName, args);
        }
        // Post-construction assembly-time writes (the AOT counterpart of a runtime assembly-time
        // filler bean): emitted after construction so the producer body still runs, before
        // registration so dependents see the fully-assembled product.
        if (provider != null) {
            for (String statement : provider.postConstruction(bean, index)) {
                wire.addStatement("$N.$L", varName, statement);
            }
        }
    }

    /**
     * The {@link AotProductConstructor} for a product, walking its supertype chain by name: the
     * loader keys providers by exact product type, and a provider registered for a base type (e.g.
     * {@code JdbcTemplate}) also assembles subclass products. The walk stops at the first match — a
     * more specific declaration wins.
     *
     * <p>The ClassInfo is needed only to read the next supertype name; a level whose ClassInfo is
     * not in the index (a narrow universe indexes only seeds and {@code @Bean} return types — a
     * product's base class can be absent) is still probed by name.
     */
    private AotProductConstructor resolveProvider(BeanDefinition bean) {
        String current = bean.qualifiedName;
        ClassInfo ci = index.getClassByName(DotName.createSimple(current));
        while (current != null) {
            AotProductConstructor provider = AotProductConstructors.forProduct(current);
            if (provider != null) {
                return provider;
            }
            if (ci == null) {
                break;
            }
            DotName superName = ci.superName();
            current = superName == null ? null : superName.toString();
            ci = current == null ? null : index.getClassByName(DotName.createSimple(current));
        }
        return null;
    }

    List<TypeSpec> configImpls() {
        return configGen.configImpls();
    }

    void generateWireMethod(
            MethodSpec.Builder wire,
            List<BeanDefinition> sortedBeans,
            Map<String, Object> overrides) {
        configGen.reset();
        for (int i = 0; i < sortedBeans.size(); i++) {
            BeanDefinition bean = sortedBeans.get(i);
            ClassName beanClass = AotTypeNames.safeClassName(bean.qualifiedName);
            String varName = bean.variableName;

            if (i > 0) {
                wire.addCode("\n");
            }

            // Engine-provided (synthetic) beans: declare a local var holding the
            // instance, then register it. The var name is the bean's own
            // variableName so @Bean methods that depend on it (e.g. EntityMetadataRegistrar
            // depending on IndexView) reference the same symbol. The construction
            // expression is supplied at the definition site via addSyntheticBean's
            // aotInstanceExpression, so this generator emits it verbatim without
            // knowing each synthetic type's construction.
            if (bean.syntheticInstance != null) {
                if (bean.aotInstanceExpression == null) {
                    // Runtime-only synthetic (the discovery IndexView): kept on the blueprint so
                    // the resolver satisfies @Bean params, but the generated container never
                    // materializes it — no AOT-path consumer exists since E1 baked the @RowModel
                    // metadata at codegen, and reconstructing the index at boot would require
                    // summer-runtime on the classpath and a boot-time scan.
                    continue;
                }
                com.palantir.javapoet.CodeBlock instanceExpr = syntheticInstanceExpression(bean);
                wire.addStatement("$T $N = $L", beanClass, varName, instanceExpr);
                wire.addStatement("builder.register($T.class, $N)", beanClass, varName);
                continue;
            }

            if (bean instanceof ConfigPropertiesBean cpb) {
                configGen.emitConfigPropertiesInstantiation(
                        wire, cpb, beanClass, varName, overrides);
            } else if (bean.isFactoryMethod()) {
                emitFactoryProductInstantiation(wire, bean, varName);
            } else {
                emitComponentInstantiation(wire, bean, beanClass, varName);
            }

            String register = bean.isFactoryMethod() ? "registerProduct" : "register";
            if (bean instanceof ConfigPropertiesBean) {
                wire.addStatement("builder.register($T.class, $N)", beanClass, varName);
            } else {
                // AOP lookup contract (one bean, one form) — mirrors
                // BeanInstantiator.registerRegularBean: for a BOUND bean varName IS the
                // $$AotProxy (the raw lives in varName_impl as its private target), so the
                // concrete-class key holds the proxy. The proxy implements only the
                // interfaces, so a typed getBean(ConcreteClass) misses the scan and fails
                // loudly with NoSuchBeanException — identical to the runtime engine's JDK
                // proxy. Unbound beans register themselves.
                wire.addStatement("builder.$L($T.class, $N)", register, beanClass, varName);
                // Register an interface key only when exactly one bean implements it (single-bean
                // lookup by interface / ctor injection). An interface with multiple impls is a
                // collection-injection strategy (List<HttpParameterResolver>, List<Middleware>)
                // resolved via getBeans — its key would otherwise be overwritten by the
                // last-writer-wins register() and hide the multi-impl contract.
                java.util.Map<String, Integer> ifaceCounts =
                        com.github.dropguard.summer.core.bean.SharedDependencyResolver
                                .interfaceImplementationCounts(sortedBeans);
                for (String iface : bean.interfaceNames) {
                    Integer count = ifaceCounts.get(iface);
                    if (count == null || count != 1) {
                        continue;
                    }
                    wire.addStatement(
                            "builder.$L($T.class, $N)",
                            register,
                            AotTypeNames.parseTypeName(iface),
                            varName);
                }
            }
        }

        // Validation Phase: run all Validator beans
        wire.addCode("\n");
        wire.addComment("Validation Phase");
        wire.beginControlFlow("for ($T bean : builder.singletons().values())", Object.class);
        wire.beginControlFlow(
                "if (bean instanceof $T validator)",
                ClassName.get("com.github.dropguard.summer.core.validation", "Validator"));
        wire.addStatement("$T targets = builder.getBeans(validator.targetType())", List.class);
        wire.beginControlFlow("for ($T target : targets)", Object.class);
        wire.addStatement("validator.validate(target)");
        wire.endControlFlow();
        wire.endControlFlow();
        wire.endControlFlow();
    }

    /**
     * Code expression for a synthetic bean's pre-built instance, usable inside the generated static
     * {@code build()} method (which has no generator state). The expression is supplied at the
     * definition site via {@code BeanDeployment#addSyntheticBean}'s {@code aotInstanceExpression},
     * so this generator never branches on the synthetic type.
     */
    private com.palantir.javapoet.CodeBlock syntheticInstanceExpression(BeanDefinition bean) {
        // The construction expression is supplied at the definition site
        // (BeanDeployment/RuntimeContainer via addSyntheticBean), so this
        // generator stays ignorant of each synthetic type. Emit it verbatim.
        // Null expressions never reach here — the emission loop skips them
        // (runtime-only synthetics such as the discovery IndexView).
        return com.palantir.javapoet.CodeBlock.of(bean.aotInstanceExpression);
    }
}
