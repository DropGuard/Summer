mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.RouteInfo.ParamBinding;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Reflection-free {@link HandlerParam} built from AOT-discovered route metadata.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>The AOT code generator constructs one of these per {@code @Pageable} (or other
@Internal
mport com.github.dropguard.summer.core.Internal;
 * resolver-driven) parameter and resolves it through the shared {@link HttpParameterResolverChain}
mport com.github.dropguard.summer.core.Internal;
 * — the same chain the runtime engine uses — so {@code @Replaces} custom resolvers behave
mport com.github.dropguard.summer.core.Internal;
 * identically on both engines.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Only resolver-backed bindings (e.g. {@code PAGEABLE}) go through the chain; {@code
mport com.github.dropguard.summer.core.Internal;
 * PATH}/{@code QUERY}/{@code BODY} are emitted inline by the generator because they have no
mport com.github.dropguard.summer.core.Internal;
 * swappable resolver.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class RouteInfoHandlerParam implements HandlerParam {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final Class<?> type;
mport com.github.dropguard.summer.core.Internal;
    private final String bindingName;
mport com.github.dropguard.summer.core.Internal;
    private final ParamBinding binding;
mport com.github.dropguard.summer.core.Internal;
    private final boolean validated;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public RouteInfoHandlerParam(
mport com.github.dropguard.summer.core.Internal;
            Class<?> type, String bindingName, ParamBinding binding, boolean validated) {
mport com.github.dropguard.summer.core.Internal;
        this.type = type;
mport com.github.dropguard.summer.core.Internal;
        this.bindingName = bindingName;
mport com.github.dropguard.summer.core.Internal;
        this.binding = binding;
mport com.github.dropguard.summer.core.Internal;
        this.validated = validated;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Class<?> type() {
mport com.github.dropguard.summer.core.Internal;
        return type;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public String bindingName() {
mport com.github.dropguard.summer.core.Internal;
        return bindingName;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public ParamBinding binding() {
mport com.github.dropguard.summer.core.Internal;
        return binding;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean validated() {
mport com.github.dropguard.summer.core.Internal;
        return validated;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
