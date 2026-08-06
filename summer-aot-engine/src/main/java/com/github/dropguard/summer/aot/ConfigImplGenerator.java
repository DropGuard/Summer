package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
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
 * Generates the strong-typed {@code $$ConfigImpl} classes and config-bean instantiation code for
 * {@code @ConfigMapping} interfaces.
 *
 * <p>Split out of {@link WireMethodGenerator}: config-binding codegen is a separate concern from DI
 * wire-body generation. Reads the Jandex index only (the AOT module is reflection-free, enforced by
 * ReflectionConfinementTest).
 */
@Internal
public final class ConfigImplGenerator {

    // Reflection-free Jandex lookups only: the AOT module must stay reflection-free (enforced by
    // ReflectionConfinementTest), so config-impl generation reads interfaces via the Jandex index
    // rather than java.lang.reflect.
    private static final DotName WITH_DEFAULT_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.config.WithDefault");
    private static final DotName WITH_NAME_DOT =
            DotName.createSimple("com.github.dropguard.summer.core.config.WithName");
    private static final short ABSTRACT = 0x400;

    private final IndexView index;
    private final List<TypeSpec> configImpls = new ArrayList<>();

    public ConfigImplGenerator(IndexView index) {
        this.index = index;
    }

    /**
     * Discards previously generated config-impl TypeSpecs (called at the start of each wire pass).
     */
    public void reset() {
        configImpls.clear();
    }

    /**
     * Generated config-impl TypeSpecs (one per @ConfigMapping interface), written as separate
     * source files by AotContextGenerator after the main container.
     */
    public List<TypeSpec> configImpls() {
        return configImpls;
    }

    /**
     * Emits the instantiation code for a config bean: a strong-typed {@code $$ConfigImpl} for
     * {@code @ConfigMapping} interfaces, or a runtime {@code ConfigBinder.bind} delegation for
     * non-interface holders.
     */
    public void emitConfigPropertiesInstantiation(
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
            com.palantir.javapoet.TypeName fieldType =
                    AotTypeNames.parseTypeName(ret.name().toString());
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
                ClassName nestedIface = AotTypeNames.safeClassName(ret.name().toString());
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
                    AotTypeNames.parseTypeName(typeName),
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
        com.palantir.javapoet.TypeName boxed = AotTypeNames.parseTypeName(typeName).box();
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
                    "Enum.valueOf($T.class, $S.toUpperCase())",
                    AotTypeNames.parseTypeName(typeName),
                    rawValue);
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
        com.palantir.javapoet.TypeName boxedType = AotTypeNames.parseTypeName(typeName).box();
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
        // Must match ConfigBinder.ConfigMappingHandler.resolveKey so the AOT-generated key equals
        // the camelCased method name (or @WithName value).
        return com.github.dropguard.summer.core.config.ConfigBinder.toCamelCase(base);
    }
}
