package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a {@code GeneratedAotContext} class that uses the unified {@link
 * com.github.dropguard.summer.core.BeanContainer} abstractions.
 *
 * <p>Dependencies are injected via constructor — no mutable state.
 */
@Internal
public final class AotContextGenerator {

    private static final Logger log = LoggerFactory.getLogger(AotContextGenerator.class);

    public static final String PACKAGE = "com.github.dropguard.summer.aot.generated";
    public static final String CLASS_NAME = "GeneratedAotContext";

    private static final String CORE_PACKAGE = "com.github.dropguard.summer.core";
    private static final ClassName BEAN_CONTAINER = ClassName.get(CORE_PACKAGE, "BeanContainer");
    private static final ClassName BEAN_CONTAINER_BUILDER =
            ClassName.get(CORE_PACKAGE, "BeanContainer", "Builder");
    private static final ClassName ENGINE = ClassName.get(CORE_PACKAGE, "Engine");
    private static final ClassName AOT_DI_MARKER = ClassName.get(CORE_PACKAGE, "AotDiMarker");
    private static final ClassName CONFIG_BINDER =
            ClassName.get("com.github.dropguard.summer.core.config", "ConfigBinder");
    private static final ClassName ROUTE_ADAPTER =
            ClassName.get(PACKAGE, "GeneratedAnnotationRouterAdapter");
    private static final ClassName ROUTE_REGISTRAR =
            ClassName.get("com.github.dropguard.summer.web", "RouterAdapter");
    private static final ClassName EXCEPTION_HANDLER_ADAPTER =
            ClassName.get(PACKAGE, "GeneratedExceptionHandlerAdapter");
    private static final ClassName EXCEPTION_HANDLER_REGISTRAR =
            ClassName.get("com.github.dropguard.summer.web", "ExceptionHandlerRegistrar");
    private static final ClassName MOCKED_BEAN =
            ClassName.get("com.github.dropguard.summer.core.bean", "MockedBean");
    private static final ClassName VALIDATOR =
            ClassName.get("com.github.dropguard.summer.core.validation", "Validator");
    private static final ClassName RESULT =
            ClassName.get("com.github.dropguard.summer.core.validation", "Result");

    private final IndexView index;
    private final File outputDir;
    private final WireMethodGenerator wireGen;
    private final java.util.Map<String, Object> profileOverrides;

    public AotContextGenerator(IndexView index, File outputDir, WireMethodGenerator wireGen) {
        this(index, outputDir, wireGen, java.util.Map.of());
    }

    public AotContextGenerator(
            IndexView index,
            File outputDir,
            WireMethodGenerator wireGen,
            java.util.Map<String, Object> profileOverrides) {
        this.index = index;
        this.outputDir = outputDir;
        this.wireGen = wireGen;
        this.profileOverrides = profileOverrides != null ? profileOverrides : java.util.Map.of();
    }

    public void generate(List<BeanDefinition> sortedBeans) throws IOException {
        generate(sortedBeans, CLASS_NAME, null);
    }

    /**
     * Generates the AOT context class under an explicit name. The default {@link #CLASS_NAME}
     * ({@code GeneratedAotContext}) is used by the production path (generated at build time by
     * {@code summer-maven-plugin}); tests pass a scope/profile-derived name so two different test
     * containers never collide on the JVM's single-load-per-name class cache.
     *
     * @param sortedBeans topologically-sorted bean definitions
     * @param className generated class name (without package)
     */
    public void generate(List<BeanDefinition> sortedBeans, String className, MockedBean[] mocks)
            throws IOException {
        log.debug("[Summer] Generating AOT context {} for {} beans", className, sortedBeans.size());
        // The generated adapter imports web types — emit it only when handlers exist,
        // or non-web applications fail to compile the generated sources.
        boolean hasExceptionHandlers =
                sortedBeans.stream().anyMatch(b -> !b.exceptionHandlerMethods.isEmpty());
        if (hasExceptionHandlers) {
            new ExceptionHandlerAdapterGenerator().generate(sortedBeans, index, outputDir);
        }

        JavaFile javaFile = buildJavaFile(sortedBeans, className, mocks);
        javaFile.writeTo(outputDir);

        // Write each generated strong-typed config impl as its own top-level class in the
        // same package, so the generated context can instantiate it directly (no proxy).
        for (TypeSpec impl : wireGen.configImpls()) {
            JavaFile.builder(PACKAGE, impl).indent("    ").build().writeTo(outputDir);
        }
    }

    private JavaFile buildJavaFile(
            List<BeanDefinition> sortedBeans, String className, MockedBean[] mocks) {
        TypeSpec.Builder type =
                TypeSpec.classBuilder(className)
                        .addModifiers(
                                javax.lang.model.element.Modifier.PUBLIC,
                                javax.lang.model.element.Modifier.FINAL);

        // Single build entry point: the boot path (SummerApplication passes the ordered
        // middleware list from apply(...) as external beans) and the test path (mocks travel
        // as a MockedBean[] element) share one build(Object...) method — a single reflective
        // contract instead of two signature-matched entry points.
        TypeSpec.Builder spec = type.addMethod(buildProductionCreateMethod(sortedBeans));
        TypeSpec built = spec.build();
        return JavaFile.builder(PACKAGE, built).indent("    ").build();
    }

    /**
     * The single build entry point: {@code build(Object... externalBeans)}. External beans are
     * registered under their own concrete class; {@link MockedBean}s (and a {@code MockedBean[]}
     * element, as passed by the test channel) are registered under their declared {@link
     * MockedBean#targetType()} (and every interface that type implements) rather than under {@code
     * instance.getClass()} — the Mockito proxy's own class. The real definition of the target type
     * has already been removed at discovery stage, so injection matches on the declared type and a
     * concrete-class {@code @Mock} resolves correctly.
     */
    private MethodSpec buildProductionCreateMethod(List<BeanDefinition> sortedBeans) {
        MethodSpec.Builder method =
                MethodSpec.methodBuilder("build")
                        .addModifiers(
                                javax.lang.model.element.Modifier.PUBLIC,
                                javax.lang.model.element.Modifier.STATIC)
                        .addParameter(Object[].class, "externalBeans")
                        .varargs(true)
                        .returns(BEAN_CONTAINER)
                        .addException(Exception.class);
        method.addStatement(
                "$T builder = new $T()", BEAN_CONTAINER_BUILDER, BEAN_CONTAINER_BUILDER);
        method.beginControlFlow("if (externalBeans != null)");
        method.beginControlFlow("for (Object bean : externalBeans)");
        method.beginControlFlow("if (bean instanceof $T mocked)", MOCKED_BEAN);
        method.addStatement("builder.register(mocked.targetType(), mocked.instance())");
        method.beginControlFlow("for (Class<?> iface : mocked.targetType().getInterfaces())");
        method.addStatement("builder.register(iface, mocked.instance())");
        method.endControlFlow();
        method.nextControlFlow("else if (bean instanceof $T[] mocksArr)", MOCKED_BEAN);
        method.beginControlFlow("for ($T mocked : mocksArr)", MOCKED_BEAN);
        method.addStatement("builder.register(mocked.targetType(), mocked.instance())");
        method.beginControlFlow("for (Class<?> iface : mocked.targetType().getInterfaces())");
        method.addStatement("builder.register(iface, mocked.instance())");
        method.endControlFlow();
        method.endControlFlow();
        method.nextControlFlow("else");
        method.addStatement("builder.register(bean.getClass(), bean)");
        method.endControlFlow();
        method.endControlFlow();
        method.endControlFlow();
        emitSharedBody(method, sortedBeans);
        return method.build();
    }

    /** Shared tail: marker, @WithDefault resolver, wire method, route + handler adapters. */
    private void emitSharedBody(MethodSpec.Builder method, List<BeanDefinition> sortedBeans) {
        method.addStatement("builder.register($T.class, new $T())", AOT_DI_MARKER, AOT_DI_MARKER);

        // Engine-provided beans (IndexView, RuntimeDiMarker, ...) arrive as synthetic
        // beans in the candidate list and are registered by WireMethodGenerator — no
        // hand-written registration here.
        wireGen.generateWireMethod(method, sortedBeans, profileOverrides);

        // Route adapter
        if (sortedBeans.stream().anyMatch(b -> !b.routes.isEmpty())) {
            method.addCode("\n");
            method.addComment("Register route adapter");
            method.addStatement("$T _routeAdapter = new $T()", ROUTE_ADAPTER, ROUTE_ADAPTER);
            method.addStatement("builder.register($T.class, _routeAdapter)", ROUTE_REGISTRAR);
        }

        // Exception handler adapter — emitted only when handlers exist. The generated
        // adapter imports web types, so emitting it unconditionally would break AOT
        // compilation for non-web applications (no summer-web on the classpath).
        boolean hasExceptionHandlers =
                sortedBeans.stream().anyMatch(b -> !b.exceptionHandlerMethods.isEmpty());
        if (hasExceptionHandlers) {
            method.addCode("\n");
            method.addComment("Register exception handler adapter");
            method.addStatement(
                    "$T _ehAdapter = new $T()",
                    EXCEPTION_HANDLER_ADAPTER,
                    EXCEPTION_HANDLER_ADAPTER);
            method.addStatement(
                    "builder.register($T.class, _ehAdapter)", EXCEPTION_HANDLER_REGISTRAR);
        }

        // Validation Phase: emitted before builder.build() so it runs on the pre-seal
        // builder.singletons() view, exactly matching RuntimeContainer's runtime path. See
        // emitValidationPhase javadoc for the parity contract.
        emitValidationPhase(method);

        method.addCode("\n");
        method.addStatement("return builder.build($T.AOT)", ENGINE);
    }

    /**
     * Emits the canonical Validation Phase for the AOT-generated {@code build()} method. Single
     * source of truth shared with {@code RuntimeContainer} — both engines must produce the same
     * validator iteration + violation accumulation, otherwise runtime/AOT parity drifts and tests
     * marked {@code @DualEngine} would diverge on validation behavior.
     *
     * <p>The generated code matches the runtime container exactly:
     *
     * <ol>
     *   <li>One {@code Result} accumulator (reused across every validator).
     *   <li>For each bean in {@code builder.singletons().values()} that is a {@code Validator<?>},
     *       resolve targets via {@code builder.getBeans(targetType)} and call {@code
     *       validate(target, result)}.
     *   <li>{@code result.throwIfInvalid()} once, after every validator has run — never
     *       mid-iteration.
     * </ol>
     *
     * <p>Why this lives here and not in {@link WireMethodGenerator}: the validation phase operates
     * on the pre-seal {@code builder.singletons()} view (every bean instantiated, none yet sealed)
     * — RuntimeContainer runs validators before {@code builder.build(Engine.RUNTIME)} for the same
     * reason, so the AOT path mirrors it 1:1.
     */
    private void emitValidationPhase(MethodSpec.Builder method) {
        method.addCode("\n");
        method.addComment("Validation Phase");
        method.addStatement("$T _validationResult = new $T()", RESULT, RESULT);
        method.beginControlFlow("for ($T _bean : builder.singletons().values())", Object.class);
        method.beginControlFlow("if (_bean instanceof $T<?> _v)", VALIDATOR);
        method.addStatement("$T<?> _targets = builder.getBeans(_v.targetType())", List.class);
        method.beginControlFlow("for ($T _target : _targets)", Object.class);
        // _v is captured as Validator<capture#1>, so validate(_target, ...) won't compile
        // against Validator<Object> without an unchecked cast — mirroring RuntimeContainer's
        // (Validator<Object>) cast at the call site.
        method.addStatement("(($T<Object>) _v).validate(_target, _validationResult)", VALIDATOR);
        method.endControlFlow();
        method.endControlFlow();
        method.endControlFlow();
        method.addStatement("_validationResult.throwIfInvalid()");
    }
}
