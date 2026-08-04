package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.bean.InjectionParameter;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

/**
 * Generates the bean instantiation body of the AOT-created {@code create()} method. Emits {@code
 * builder.register(...)} calls for each bean.
 *
 * <p>Handles constructor injection, {@code @Bean} method invocation, {@code @ConfigMapping}
 * binding, AOP proxy wrapping, and interface registration.
 */
@Internal
public final class WireMethodGenerator {

    private static final ClassName JDBC_TEMPLATE =
            ClassName.get("com.github.dropguard.summer.data.jdbc", "JdbcTemplate");

    // Reflection-free Jandex lookups only: the AOT module must stay reflection-free (enforced by
    // ReflectionConfinementTest), so config-impl generation reads interfaces via the Jandex index
    // rather than java.lang.reflect.
    private static final DotName WITH_DEFAULT_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.config.WithDefault");
    private static final DotName WITH_NAME_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.config.WithName");
    private static final short ABSTRACT = 0x400;

    private final IndexView index;

    public WireMethodGenerator(IndexView index) {
        this(index, Map.of());
    }

    public WireMethodGenerator(IndexView index, java.util.Map<String, Object> overrides) {
        this.index = index;
        this.overrides = overrides != null ? overrides : Map.of();
    }

    /**
     * Emits inline {@code RowMapper} lambda registrations for all {@code @RowModel} records in the
     * index. Mappers are registered directly on the {@code JdbcTemplate} singleton via {@code
     * registerMapper()}.
     */
    void emitRowMapperRegistrations(
            MethodSpec.Builder wire,
            IndexView index,
            java.util.Set<String> activeClassNames,
            List<BeanDefinition> sortedBeans) {

        if (index == null) {
            return;
        }

        List<com.github.dropguard.summer.data.jdbc.RowModelMeta> metas =
                com.github.dropguard.summer.data.jdbc.RowMapperFactory.scanJandex(index);

        if (activeClassNames != null) {
            metas =
                    metas.stream()
                            .filter(m -> activeClassNames.contains(m.modelClassName()))
                            .toList();
        }

        if (metas.isEmpty()) {
            return;
        }

        wire.addCode("\n");
        wire.addComment("Register @RowModel mappers with JdbcTemplate");
        wire.addStatement(
                "$T _jt = ($T) builder.peek($T.class)",
                JDBC_TEMPLATE,
                JDBC_TEMPLATE,
                JDBC_TEMPLATE);
        wire.beginControlFlow("if (_jt != null)");

        for (var meta : metas) {
            // scanJandex() already validates field types (fail-fast on both engines),
            // so no separate assertion is needed here.

            ClassName modelClass = safeClassName(meta.modelClassName());
            var mapperVar = meta.simpleName().toLowerCase(java.util.Locale.ROOT) + "Mapper";

            wire.addCode("\n");
            wire.addComment(meta.simpleName() + " RowMapper");
            wire.addStatement(
                    "$T<$T> $N = ($N, $N) -> {",
                    ClassName.get("com.github.dropguard.summer.data.jdbc", "RowMapper"),
                    modelClass,
                    mapperVar,
                    "rs",
                    "rowNum");
            for (var field : meta.fields()) {
                String colName =
                        com.github.dropguard.summer.data.jdbc.RowMapperFactory.camelToSnake(
                                field.name());
                CodeBlock readExpr = TypeReads.jdbcRead(colName, field.typeName());
                wire.addStatement(
                        "    $T $N = $L",
                        TypeReads.typeName(field.typeName()),
                        field.name(),
                        readExpr);
            }
            StringBuilder ctorArgs = new StringBuilder();
            for (int i = 0; i < meta.fields().size(); i++) {
                if (i > 0) ctorArgs.append(", ");
                ctorArgs.append(meta.fields().get(i).name());
            }
            wire.addStatement("    return new $T($L)", modelClass, ctorArgs.toString());
            wire.addStatement("}");
            wire.addStatement("_jt.registerMapper($T.class, $N)", modelClass, mapperVar);
        }

        wire.endControlFlow();
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
                            : safeClassName(bean.interfaceNames.get(0));
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
                return CodeBlock.of("java.util.List.of()");
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
            // BeanContainer is not available during AOT build phase
            return CodeBlock.of("null");
        }
        if (parameter.resolved().isEmpty()) {
            return CodeBlock.of("null");
        }
        return CodeBlock.of("$N", parameter.resolved().get(0).variableName);
    }

    private void emitFactoryProductInstantiation(
            MethodSpec.Builder wire, BeanDefinition bean, String varName) {
        ClassName producedClass = safeClassName(bean.qualifiedName);
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

    // Override content is known at code-generation time (from @TestProfile), so it
    // is inlined into the generated wire() method as a BindingContext literal
    // rather than read from a runtime ThreadLocal.
    private final Map<String, Object> overrides;
    private final List<TypeSpec> configImpls = new ArrayList<>();

    /**
     * Generated config-impl TypeSpecs (one per @ConfigMapping interface), written as separate
     * source files by AotContextGenerator after the main container.
     */
    List<TypeSpec> configImpls() {
        return configImpls;
    }

    void generateWireMethod(MethodSpec.Builder wire, List<BeanDefinition> sortedBeans) {
        generateWireMethod(wire, sortedBeans, overrides);
    }

    void generateWireMethod(
            MethodSpec.Builder wire,
            List<BeanDefinition> sortedBeans,
            Map<String, Object> overrides) {
        configImpls.clear();
        for (int i = 0; i < sortedBeans.size(); i++) {
            BeanDefinition bean = sortedBeans.get(i);
            ClassName beanClass = safeClassName(bean.qualifiedName);
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
                emitConfigPropertiesInstantiation(wire, cpb, beanClass, varName, overrides);
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
                            "builder.register($T.class, $N)", parseTypeName(iface), varName);
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

    private void emitConfigPropertiesInstantiation(
            MethodSpec.Builder wire,
            ConfigPropertiesBean bean,
            ClassName beanClass,
            String varName,
            Map<String, Object> overrides) {
        ClassName configBinder =
                ClassName.get("com.github.dropguard.summer.core.config", "ConfigBinder");
        ClassName typeConverter =
                ClassName.get("com.github.dropguard.summer.core.config", "TypeConverter");
        String prefix = bean.configPropertiesPrefix != null ? bean.configPropertiesPrefix : "";
        // @TestProfile overrides are baked in as a BindingContext literal so the same
        // container identity (derived from override content) and binding read from one
        // explicit source and never drift (no ThreadLocal, no remove()). @WithDefault
        // values are NOT injected here: they are resolved inside the generated
        // $$ConfigImpl constructor lazily (only when the key is absent), mirroring the
        // Runtime proxy's behaviour. Injecting them eagerly as a strong override would
        // (a) force eager conversion of every default — including complex types such as
        // List, which TypeConverter cannot coerce from a String — and (b) mask YAML values.
        CodeBlock ctxLiteral;
        if (overrides.isEmpty()) {
            ctxLiteral = CodeBlock.of("$T.BindingContext.of()", configBinder);
        } else {
            ctxLiteral =
                    CodeBlock.of(
                            "$T.BindingContext.of($L)",
                            configBinder,
                            buildOverridesLiteral(overrides));
        }
        // Two target shapes, two AOT strategies, both avoiding the legacy runtime
        // binders (Jackson convertValue for records, dynamic Proxy for interfaces):
        //   - @ConfigMapping interface  -> a strong-typed $$ConfigImpl class whose
        //     final fields are copied straight from the resolved section map. No Jackson,
        //     no Proxy.
        ClassInfo targetClass = index.getClassByName(DotName.createSimple(bean.qualifiedName));
        if (targetClass != null && targetClass.isInterface()) {
            TypeSpec impl = generateConfigImpl(beanClass, targetClass);
            configImpls.add(impl);
            // All generated config-impl classes live in AotContextGenerator.PACKAGE, so the
            // reference must use that package — not the interface's own package.
            ClassName implClass =
                    ClassName.get(
                            AotContextGenerator.PACKAGE, beanClass.simpleName() + "$$ConfigImpl");
            wire.addStatement(
                    "$T $N = new $T(new $T().bindSection($L, $S))",
                    implClass,
                    varName,
                    implClass,
                    configBinder,
                    ctxLiteral,
                    prefix);
        } else {
            // Non-interface config holders (records, plain classes) fall back to the runtime
            // Jackson binder (ConfigBinder.bind). The AOT strong-typed generation targets the
            // @ConfigMapping interface path above; this branch stays a single delegation so it
            // never diverges from the Runtime engine.
            wire.addStatement(
                    "$T $N = $T.bind($L, $S, $T.class)",
                    beanClass,
                    varName,
                    configBinder,
                    ctxLiteral,
                    prefix,
                    beanClass);
        }
    }

    private static CodeBlock buildOverridesLiteral(Map<String, Object> overrides) {
        CodeBlock.Builder cb = CodeBlock.builder();
        cb.add("java.util.Map.of(");
        boolean first = true;
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            if (!first) {
                cb.add(", ");
            }
            cb.add("$S, $S", e.getKey(), String.valueOf(e.getValue()));
            first = false;
        }
        cb.add(")");
        return cb.build();
    }

    /**
     * Builds a {@code Map.of(name, TypeConverter.convert(raw, Type.class), ...)} literal. Each
     * default is converted at generated-code time (statically typed), so the runtime bind call
     * applies defaults with zero reflection.
     */
    private static CodeBlock buildDefaultsLiteral(
            Map<String, String> defaults, Map<String, String> typeNames) {
        CodeBlock.Builder cb = CodeBlock.builder();
        cb.add("java.util.Map.of(");
        boolean first = true;
        for (Map.Entry<String, String> e : defaults.entrySet()) {
            if (!first) {
                cb.add(", ");
            }
            cb.add(
                    "$S, $T.convert($S, $T.class)",
                    e.getKey(),
                    ClassName.get("com.github.dropguard.summer.core.config", "TypeConverter"),
                    e.getValue(),
                    parseTypeName(typeNames.get(e.getKey())));
            first = false;
        }
        cb.add(")");
        return cb.build();
    }

    /**
     * Converts a JVM type name to a JavaPoet {@link com.palantir.javapoet.TypeName}. The primitive
     * mapping is delegated to {@link TypeReads#typeName(String)}; this method adds array /
     * nested-class ({@code $}) preprocessing that only applies to Jandex-derived type names.
     */
    static com.palantir.javapoet.TypeName parseTypeName(String typeName) {
        if (typeName.startsWith("[")) return ClassName.get(Object.class);
        if (PrimitiveTypes.isPrimitive(typeName)) return TypeReads.typeName(typeName);
        // Jandex rawType names use JVM internal '$' for nested classes (e.g.
        // WebConfig$RouterType). Render the source form WebConfig.RouterType via
        // ClassName's nested-class constructor so the generated import resolves.
        String dotted = typeName.replace('$', '.');
        int lastDot = dotted.lastIndexOf('.');
        String pkg = dotted.substring(0, lastDot);
        String[] nested = dotted.substring(lastDot + 1).split("\\.");
        if (nested.length == 1) {
            return ClassName.get(pkg, nested[0]);
        }
        return ClassName.get(
                pkg, nested[0], java.util.Arrays.copyOfRange(nested, 1, nested.length));
    }

    /**
     * Creates a {@link ClassName} from a qualified name that may contain JVM-internal {@code $}
     * nested-class separators. Replaces {@code $} with {@code .} so the generated source uses valid
     * Java syntax.
     */
    private static ClassName safeClassName(String qualifiedName) {
        return ClassName.bestGuess(qualifiedName.replace('$', '.'));
    }

    /**
     * Builds a strong-typed implementation of a {@code @ConfigMapping} config interface. Every
     * abstract method becomes a {@code final} field initialized from the section map passed to the
     * constructor. Defaults ({@code @WithDefault}) and nested mappings are resolved at
     * code-generation time via the Jandex index (the AOT module is reflection-free), so the
     * generated class binds with zero reflection at runtime. Nested config interfaces recurse into
     * their own {@code $$ConfigImpl} classes (also collected into {@link #configImpls}).
     */
    private TypeSpec generateConfigImpl(ClassName iface, ClassInfo classInfo) {
        String implName = iface.simpleName() + "$$ConfigImpl";
        String qualifiedName = classInfo.name().toString();
        TypeSpec.Builder impl =
                TypeSpec.classBuilder(implName)
                        .addModifiers(
                                javax.lang.model.element.Modifier.PUBLIC,
                                javax.lang.model.element.Modifier.FINAL)
                        .addSuperinterface(iface);
        ClassName missingEx =
                ClassName.get(
                        "com.github.dropguard.summer.core.exception", "MissingFieldException");
        ClassName typeConverter =
                ClassName.get("com.github.dropguard.summer.core.config", "TypeConverter");
        MethodSpec.Builder ctor =
                MethodSpec.constructorBuilder()
                        .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                        .addParameter(ClassName.get("java.util", "Map"), "__section");
        for (MethodInfo m : classInfo.methods()) {
            if ((m.flags() & ABSTRACT) == 0) {
                continue;
            }
            String name = m.name();
            String key = resolveKey(m);
            Type ret = m.returnType();
            com.palantir.javapoet.TypeName fieldType = parseTypeName(ret.name().toString());
            boolean isList = ret.name().toString().equals("java.util.List");
            boolean isMap = ret.name().toString().equals("java.util.Map");
            boolean isNestedInterface =
                    ret.kind() == Type.Kind.CLASS && !isMap && !isList && isInterfaceType(ret);
            AnnotationInstance wdAnn = m.annotation(WITH_DEFAULT_DOT);
            // A required key (no @WithDefault) is stored boxed so a missing value can be
            // represented as null and the MissingFieldException deferred to the getter —
            // exactly how the Runtime proxy behaves (lazy on access). The AOT engine
            // instantiates every config bean eagerly, so an eager throw would surface configs
            // that are simply never read (e.g. an optional TLS block), diverging from Runtime.
            boolean required = wdAnn == null;
            com.palantir.javapoet.TypeName storeType =
                    (required && PrimitiveTypes.isPrimitive(ret.name().toString()))
                            ? fieldType.box()
                            : fieldType;
            impl.addField(
                    FieldSpec.builder(
                                    storeType,
                                    name,
                                    javax.lang.model.element.Modifier.PRIVATE,
                                    javax.lang.model.element.Modifier.FINAL)
                            .build());
            if (isNestedInterface) {
                ClassName nestedIface = safeClassName(ret.name().toString());
                TypeSpec nested = generateConfigImpl(nestedIface, index.getClassByName(ret.name()));
                configImpls.add(nested);
                // Nested impl also lives in AotContextGenerator.PACKAGE (all config impls do).
                ClassName nestedImpl =
                        ClassName.get(
                                AotContextGenerator.PACKAGE,
                                nestedIface.simpleName() + "$$ConfigImpl");
                ctor.addStatement(
                        "this.$N = (__section.get($S) != null) ? new $T((java.util.Map<String,"
                                + " Object>) __section.get($S)) : null",
                        name,
                        key,
                        nestedImpl,
                        key);
            } else if (wdAnn != null) {
                String wdValue = wdAnn.value().asString();
                CodeBlock coerced = coerceExpr(ret, key, "__section", typeConverter);
                CodeBlock defaulted = defaultExpr(ret, wdValue, typeConverter);
                ctor.addStatement(
                        "this.$N = (__section.get($S) != null) ? $L : $L",
                        name,
                        key,
                        coerced,
                        defaulted);
            } else {
                // Required key: store null if absent; the getter raises MissingFieldException
                // lazily (see below), matching the Runtime proxy's access-time semantics.
                CodeBlock coerced = coerceExpr(ret, key, "__section", typeConverter);
                ctor.addStatement(
                        "this.$N = (__section.get($S) != null) ? $L : null", name, key, coerced);
            }
            // Lazy missing-key check for required fields (matches the Runtime proxy, which
            // throws from the getter rather than at construction).
            MethodSpec.Builder getter =
                    MethodSpec.methodBuilder(name)
                            .addAnnotation(java.lang.Override.class)
                            .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                            .returns(fieldType);
            if (required) {
                getter.addStatement(
                        "if (this.$N == null) throw new $T($S, $S, $S)",
                        name,
                        missingEx,
                        key,
                        iface.simpleName(),
                        "Missing required config key '" + key + "' for " + qualifiedName);
            }
            getter.addStatement("return $N", name);
            impl.addMethod(getter.build());
        }
        impl.addMethod(ctor.build());
        return impl.build();
    }

    private boolean isInterfaceType(Type ret) {
        ClassInfo ci = index.getClassByName(ret.name());
        return ci != null && ci.isInterface();
    }

    private boolean isEnumType(Type ret) {
        ClassInfo ci = index.getClassByName(ret.name());
        return ci != null && ci.isEnum();
    }

    /** Coerces the raw section value to the method's return type (used when the key is present). */
    private CodeBlock coerceExpr(Type ret, String key, String sectionVar, ClassName typeConverter) {
        String typeName = ret.name().toString();
        if (typeName.equals("java.lang.String")) {
            return CodeBlock.of("(String) $N.get($S)", sectionVar, key);
        }
        if (isEnumType(ret)) {
            return CodeBlock.of(
                    "Enum.valueOf($T.class, ((String) $N.get($S)).toUpperCase())",
                    parseTypeName(typeName),
                    sectionVar,
                    key);
        }
        if (typeName.equals("java.util.List")) {
            return CodeBlock.of("(java.util.List) $N.get($S)", sectionVar, key);
        }
        if (typeName.equals("java.util.Map")) {
            return CodeBlock.of("(java.util.Map) $N.get($S)", sectionVar, key);
        }
        // Scalars (incl. primitives such as long/int): the resolved section value may already be a
        // Number (e.g. an Integer from YAML), so route through TypeConverter — which coerces
        // Numbers, not just Strings — rather than a bare (long) cast that would throw
        // ClassCastException on an Integer. The boxed target type matches how required primitives
        // are stored.
        com.palantir.javapoet.TypeName boxed = parseTypeName(typeName).box();
        return CodeBlock.of(
                "($T) $T.convert($N.get($S), $T.class)",
                boxed,
                typeConverter,
                sectionVar,
                key,
                boxed);
    }

    /** Builds the literal used when a key is absent but {@code @WithDefault} is present. */
    private CodeBlock defaultExpr(Type ret, String rawValue, ClassName typeConverter) {
        String typeName = ret.name().toString();
        if (isEnumType(ret)) {
            return CodeBlock.of(
                    "Enum.valueOf($T.class, $S.toUpperCase())", parseTypeName(typeName), rawValue);
        }
        if (typeName.equals("java.util.List")) {
            return CodeBlock.of("java.util.List.of()");
        }
        if (typeName.equals("java.util.Map")) {
            return CodeBlock.of("java.util.Map.of()");
        }
        if (typeName.equals("java.lang.String")) {
            return CodeBlock.of("$S", rawValue);
        }
        // Scalars: TypeConverter.convert returns Object, so cast it back to the (boxed) target
        // type. .box() yields the boxed type for primitives (no .class literal otherwise), and is a
        // no-op for references.
        com.palantir.javapoet.TypeName boxedType = parseTypeName(typeName).box();
        return CodeBlock.of(
                "($T) $T.convert($S, $T.class)", boxedType, typeConverter, rawValue, boxedType);
    }

    /**
     * Resolves the YAML key for a config interface method, mirroring {@code
     * ConfigBinder.resolveKey}: {@code @WithName} wins, otherwise the method name is camelCased.
     */
    private String resolveKey(MethodInfo m) {
        AnnotationInstance wn = m.annotation(WITH_NAME_DOT);
        String base =
                (wn != null && !wn.value().asString().isEmpty()) ? wn.value().asString() : m.name();
        // Must match ConfigBinder.ConfigMappingHandler.resolveKey and Discovery.resolveKeyName
        // so the AOT-generated key equals the camelCased method name (or @WithName value).
        return com.github.dropguard.summer.core.config.ConfigBinder.toCamelCase(base);
    }
}
