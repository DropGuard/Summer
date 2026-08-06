package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.bean.InjectionParameter;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.IndexView;

/**
 * Generates the bean instantiation body of the AOT-created {@code create()} method. Emits {@code
 * builder.register(...)} calls for each bean.
 *
 * <p>Facade over the codegen pipeline: constructor injection / {@code @Bean} invocation / AOP proxy
 * wrapping / interface registration are emitted here; {@code @ConfigMapping} binding and
 * {@code @RowModel} row-mapper registration are delegated to {@link ConfigImplGenerator} and {@link
 * RowMapperEmitter} respectively.
 */
@Internal
public final class WireMethodGenerator {

    private final IndexView index;
    private final ConfigImplGenerator configGen;
    private final RowMapperEmitter rowMapperEmitter;

    public WireMethodGenerator(IndexView index) {
        this.index = index;
        this.configGen = new ConfigImplGenerator(index);
        this.rowMapperEmitter = new RowMapperEmitter(index);
    }

    /**
     * Emits inline {@code RowMapper} lambda registrations for {@code @RowModel} records. Delegates
     * to {@link RowMapperEmitter}.
     */
    void emitRowMapperRegistrations(
            MethodSpec.Builder wire,
            java.util.Set<String> activeClassNames,
            List<BeanDefinition> sortedBeans) {
        rowMapperEmitter.emitRowMapperRegistrations(wire, activeClassNames, sortedBeans);
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
        }
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
        // Scalar (non-List) parameter.
        if (parameter.typeName().equals("com.github.dropguard.summer.core.BeanContainer")) {
            // Mirror the runtime engine (BeanInstantiator): injecting the container into a bean
            // would create a circular bootstrap reference. The runtime engine rejects it at
            // instantiation; the AOT engine must reject it at codegen time, or the generated
            // container silently accepts a graph the runtime engine refuses — a dual-engine
            // divergence.
            throw new com.github.dropguard.summer.core.exception.BeanCreationException(
                    "ApplicationContext injection is not supported by the AOT engine."
                            + " Use BeanContainer from caller.");
        }
        if (parameter.resolved().isEmpty()) {
            // A dependency on a mocked type: its bean definition was removed at discovery and
            // the mock instance is registered on the builder before the wire loop runs
            // (build(Object...) mock branch). Resolve it from the builder at runtime — injecting
            // null here would silently hand the dependent bean a null instead of the mock.
            ClassName paramClass = AotTypeNames.safeClassName(parameter.typeName());
            return CodeBlock.of("($T) builder.peek($T.class)", paramClass, paramClass);
        }
        return CodeBlock.of("$N", parameter.resolved().get(0).variableName);
    }

    private void emitFactoryProductInstantiation(
            MethodSpec.Builder wire, BeanDefinition bean, String varName) {
        ClassName producedClass = AotTypeNames.safeClassName(bean.qualifiedName);
        String configVar = bean.configBeanDefinition.variableName;
        String methodName = bean.producerMethodName;
        CodeBlock args = buildConstructorArgs(bean);

        if (bean.parameters.isEmpty()) {
            wire.addStatement("$T $N = $N.$N()", producedClass, varName, configVar, methodName);
        } else {
            wire.addStatement(
                    "$T $N = $N.$N($L)", producedClass, varName, configVar, methodName, args);
        }
    }

    /**
     * Generated config-impl TypeSpecs (one per @ConfigMapping interface), written as separate
     * source files by AotContextGenerator after the main container.
     */
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

            if (bean instanceof ConfigPropertiesBean) {
                wire.addStatement("builder.register($T.class, $N)", beanClass, varName);
            } else {
                if (bean.needsProxy() && !bean.interfaceNames.isEmpty()) {
                    wire.addStatement(
                            "builder.register($T.class, $N)", beanClass, varName + "_impl");
                } else {
                    wire.addStatement("builder.register($T.class, $N)", beanClass, varName);
                }
                for (String iface : bean.interfaceNames) {
                    wire.addStatement(
                            "builder.register($T.class, $N)",
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
        wire.addStatement("$T target = builder.peek(validator.targetType())", Object.class);
        wire.beginControlFlow("if (target != null)");
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
        if (bean.aotInstanceExpression == null) {
            throw new IllegalStateException(
                    "Synthetic bean has no AOT instance expression: " + bean.qualifiedName);
        }
        return com.palantir.javapoet.CodeBlock.of(bean.aotInstanceExpression);
    }
}
