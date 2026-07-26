package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.bean.RouteInfo.ParamBinding;

/**
 * Reflection-free {@link HandlerParam} built from AOT-discovered route
 * metadata.
 *
 * <p>
 * The AOT code generator constructs one of these per {@code @Pageable} (or
 * other resolver-driven) parameter and resolves it through the shared
 * {@link HttpParameterResolverChain} — the same chain the runtime engine uses —
 * so {@code @Replaces} custom resolvers behave identically on both engines.
 * </p>
 *
 * <p>
 * Only resolver-backed bindings (e.g. {@code PAGEABLE}) go through the chain;
 * {@code PATH}/{@code QUERY}/{@code BODY} are emitted inline by the generator
 * because they have no swappable resolver.
 * </p>
 */
public final class RouteInfoHandlerParam implements HandlerParam {

	private final Class<?> type;
	private final String bindingName;
	private final ParamBinding binding;
	private final boolean validated;

	public RouteInfoHandlerParam(Class<?> type, String bindingName, ParamBinding binding, boolean validated) {
		this.type = type;
		this.bindingName = bindingName;
		this.binding = binding;
		this.validated = validated;
	}

	@Override
	public Class<?> type() {
		return type;
	}

	@Override
	public String bindingName() {
		return bindingName;
	}

	@Override
	public ParamBinding binding() {
		return binding;
	}

	@Override
	public boolean validated() {
		return validated;
	}
}
