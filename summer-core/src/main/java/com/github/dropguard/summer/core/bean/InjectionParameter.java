package com.github.dropguard.summer.core.bean;

import com.github.dropguard.summer.core.Internal;
import java.util.List;

/**
 * One injection parameter of a bean, as a first-class part of {@link BeanDefinition}.
 *
 * <p>This is the type that finally makes {@link BeanDefinition} honest about its parameters.
 * Previously a parameter's information was shredded across four fields — {@code
 * constructorParamTypes}/{@code producerParamTypes} ({@code List<String>}), {@code
 * listElementTypes} ({@code Map<Integer,String>}), and the resolver's flat {@code
 * resolvedDependencies} — so every consumer (the AOT generator, the runtime instantiator) rebuilt
 * the "position → dependency" mapping with its own cursor or reflection logic. That duplicated
 * logic, all deriving the same structure from parallel collections, was the smell.
 *
 * <p>Here a parameter owns its complete information: its type, and the dependencies the resolver
 * resolved for it. The type name keeps its generic arguments ({@code
 * java.util.List<com.github.dropguard.summer.aot.testfixtures.Foo>}, not just {@code
 * java.util.List}) — the modelling phase never suffers type erasure, so no parallel collection or
 * post-hoc element-type map is needed. Position is the index in {@link BeanDefinition#parameters};
 * consumers read parameters directly.
 *
 * <p>Whether a parameter is a {@code List} and what its element type is are <em>derived</em> from
 * {@link #typeName()} — not stored as a separate flag or sub-type. {@link #elementType()} is a
 * stateless accessor that extracts the {@code <...>} argument; it carries no information beyond the
 * type name, it merely centralises the one bit of parsing the consumers share.
 */
@Internal
public record InjectionParameter(String typeName, List<BeanDefinition> resolved) {

    /**
     * The element type of a {@code List<T>} parameter, e.g. {@code
     * com.github.dropguard.summer.aot.testfixtures.Foo} from {@code
     * java.util.List<com.github.dropguard.summer.aot.testfixtures.Foo>}. Empty for non-list
     * parameters. Derived from {@link #typeName()} — no separate storage, the modelling phase keeps
     * the generic argument so this is just string extraction. Consumers detect a {@code List}
     * parameter via {@code typeName().startsWith( "java.util.List<")} directly; no {@code isList()}
     * flag is stored.
     */
    public String elementType() {
        if (!typeName.startsWith("java.util.List<") || !typeName.endsWith(">")) return "";
        return typeName.substring("java.util.List<".length(), typeName.length() - 1);
    }
}
